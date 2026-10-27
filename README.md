# calculator-devops

A Calculator REST API built with **Spring Boot 3.5 / Java 21 / Maven**, delivered by a fully automated CI/CD pipeline:

```
git push ──► GitHub ──► Jenkins ──► Docker Hub ──► Docker Compose (Nginx + 2 API replicas)
             (webhook /   Checkout → Build → Test → Docker Build → Push → Deploy
              poll SCM)                                                   │
                                                                          └─► e-mail notification
```

## API

| Operation | Endpoint                           | Behaviour                                  |
|-----------|------------------------------------|--------------------------------------------|
| Add       | `GET /api/v1/add?a=&b=`            | `a + b`                                    |
| Subtract  | `GET /api/v1/subtract?a=&b=`       | `a - b`                                    |
| Multiply  | `GET /api/v1/multiply?a=&b=`       | `a * b`                                    |
| Divide    | `GET /api/v1/divide?a=&b=`         | `a / b`; `b = 0` → HTTP 400                |
| Health    | `GET /actuator/health`             | Spring Boot Actuator probe (`UP` / `DOWN`) |

Success:

```json
GET /api/v1/add?a=2&b=3
{ "operation": "add", "a": 2, "b": 3, "result": 5 }
```

Errors (division by zero, missing or non-numeric parameter) return **HTTP 400** with a structured body:

```json
GET /api/v1/divide?a=1&b=0
{
  "timestamp": "2026-10-27T13:58:19.087Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Division by zero is not allowed",
  "path": "/api/v1/divide"
}
```

Numbers are handled as `BigDecimal` (16 significant digits), so `0.1 + 0.2` returns exactly `0.3`.

## Project layout

```
├── src/main/java/com/spe/calculator/
│   ├── CalculatorApplication.java
│   ├── controller/CalculatorController.java      # REST endpoints
│   ├── service/CalculatorService.java            # arithmetic
│   ├── exception/GlobalExceptionHandler.java     # 400 error bodies
│   └── model/                                    # response records
├── src/test/java/com/spe/calculator/
│   ├── service/CalculatorServiceTest.java        # JUnit 5 unit tests
│   └── controller/CalculatorControllerIntegrationTest.java   # MockMvc tests
├── pom.xml, mvnw, .mvn/                          # Maven build + wrapper
├── Dockerfile, .dockerignore                     # multi-stage image
├── docker-compose.yml, nginx/nginx.conf          # proxy + replicas
└── Jenkinsfile                                   # CI/CD pipeline
```

## Build, test, run locally

Requires JDK 21 (Maven is provided by the wrapper).

```bash
./mvnw clean package          # compile, test, and build target/calculator.jar
./mvnw test                   # tests only; JUnit XML in target/surefire-reports/
java -jar target/calculator.jar
curl "localhost:8080/api/v1/multiply?a=6&b=7"
```

## Docker

```bash
docker build -t calculator-devops .
docker run -d -p 8080:8080 calculator-devops
curl localhost:8080/actuator/health
```

The Dockerfile has two stages:

1. **build** (`maven:3.9.11-eclipse-temurin-21`): copies `pom.xml` first and runs `dependency:go-offline`, so the
   dependency layer stays cached until the POM changes. Then it copies `src/` and packages the JAR.
2. **runtime** (`eclipse-temurin:21-jre-alpine`): contains only a JRE and the JAR, runs as a non-root `app` user,
   `EXPOSE 8080`, and has a `HEALTHCHECK` against `/actuator/health`.

## Docker Compose

```bash
DOCKERHUB_USER=<your-dockerhub-user> IMAGE_TAG=latest docker compose up -d --wait
curl "localhost:8085/api/v1/add?a=2&b=3"
docker compose ps
docker compose down
```

| Service      | What it does                                                                                     |
|--------------|--------------------------------------------------------------------------------------------------|
| `calculator` | The API, running as **2 replicas** (`CALCULATOR_REPLICAS`), with an actuator health check and `restart: unless-stopped`. |
| `proxy`      | Nginx reverse proxy on host port `8085` (`PROXY_PORT`). It starts only after the API is healthy and round-robins between the replicas. |

Both services share the `calculator-net` bridge network. Each proxied response carries an `X-Upstream` header
showing which replica served it: `curl -si localhost:8085/api/v1/add?a=1\&b=1 | grep X-Upstream`.

## CI/CD pipeline (Jenkinsfile)

| Stage        | What it does                                                                           |
|--------------|----------------------------------------------------------------------------------------|
| Checkout     | Checks out the commit that triggered the build                                         |
| Build        | `./mvnw clean package -DskipTests`                                                     |
| Test         | `./mvnw test`; publishes JUnit results; a failing test fails the build                 |
| Docker Build | Builds `<user>/calculator-devops:<BUILD_NUMBER>` and `:latest`                         |
| Push         | `docker login` with the Jenkins credential `dockerhub-credentials`, then pushes both tags |
| Deploy       | `docker compose up -d --wait --remove-orphans`, then checks `/actuator/health` through the proxy |
| post         | Sends an e-mail with the build status (success or failure) to the Jenkins default recipients |

- **Triggers:** GitHub webhook (`githubPush`), with Poll SCM every ~2 minutes as a fallback.
- **Idempotent:** `docker compose up -d` only recreates containers whose image or config changed, and
  `disableConcurrentBuilds()` stops two deployments from overlapping.
- **No secrets in the repo:** Docker Hub credentials and SMTP settings live only in Jenkins.

### Jenkins setup

1. **Agent prerequisites:** JDK 21 (`openjdk-21-jdk-headless`), Docker with the Compose v2 plugin, and the
   `jenkins` user in the `docker` group (`sudo usermod -aG docker jenkins && sudo systemctl restart jenkins`).
2. **Plugins:** Pipeline, Git, GitHub, JUnit, Credentials Binding, Email Extension, Timestamper.
3. **Credentials:** *Manage Jenkins → Credentials → System → Global → Add*. Choose *Username with password*,
   set ID `dockerhub-credentials`, and use your Docker Hub username with an access token as the password.
4. **E-mail:** *Manage Jenkins → System → Extended E-mail Notification*. Set SMTP server `smtp.gmail.com`,
   port `465`, check *Use SSL*, add a credential with your Gmail address and an
   [app password](https://myaccount.google.com/apppasswords), and set *Default Recipients* to your e-mail.
5. **Job:** *New Item → Pipeline*. Under *Pipeline script from SCM*, choose Git, enter the repository URL and
   branch `*/main`, with script path `Jenkinsfile`. Run the job once by hand so Jenkins registers the triggers.
6. **Webhook (optional):** GitHub → *Settings → Webhooks*, URL `https://<public-jenkins-url>/github-webhook/`
   (for a local Jenkins, expose it with a tunnel such as ngrok). Without a webhook, Poll SCM picks up pushes
   within ~2 minutes.
