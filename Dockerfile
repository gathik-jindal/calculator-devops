# syntax=docker/dockerfile:1

# ---------- Stage 1: build the JAR with Maven + JDK ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build

# Copy only the POM first so the dependency layer is cached
# until pom.xml changes, not on every source edit.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: slim JRE runtime ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build --chown=app:app /build/target/calculator.jar app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# JVM flags can be passed at run time via JAVA_TOOL_OPTIONS
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
