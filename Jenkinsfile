// Declarative CI/CD pipeline for the calculator service.
//
// Jenkins setup required (see README "Jenkins setup"):
//   - Credentials: "dockerhub-credentials" (Username with password; password = Docker Hub access token)
//   - Extended E-mail Notification configured with SMTP + Default Recipients
//   - The jenkins user is in the "docker" group and a JDK 21 is installed on the agent

pipeline {
    agent any

    triggers {
        githubPush()                 // GitHub webhook (needs Jenkins reachable from GitHub)
        pollSCM('H/2 * * * *')       // fallback: poll GitHub every ~2 minutes
    }

    options {
        timestamps()
        disableConcurrentBuilds()    // never run two deployments at once
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 20, unit: 'MINUTES')
    }

    environment {
        DOCKERHUB  = credentials('dockerhub-credentials')   // exposes DOCKERHUB_USR / DOCKERHUB_PSW
        IMAGE_NAME = 'calculator-devops'
        IMAGE_TAG  = "${env.BUILD_NUMBER}"
        PROXY_PORT = '8085'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                sh 'chmod +x mvnw && ./mvnw -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build \
                      -t "$DOCKERHUB_USR/$IMAGE_NAME:$IMAGE_TAG" \
                      -t "$DOCKERHUB_USR/$IMAGE_NAME:latest" .
                '''
            }
        }

        stage('Push') {
            steps {
                sh '''
                    echo "$DOCKERHUB_PSW" | docker login -u "$DOCKERHUB_USR" --password-stdin
                    docker push "$DOCKERHUB_USR/$IMAGE_NAME:$IMAGE_TAG"
                    docker push "$DOCKERHUB_USR/$IMAGE_NAME:latest"
                '''
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy') {
            steps {
                // "up -d" only recreates containers whose image/config changed, so a
                // re-run with the same tag leaves the running stack untouched.
                sh '''
                    export DOCKERHUB_USER="$DOCKERHUB_USR"
                    docker compose up -d --wait --wait-timeout 120 --remove-orphans
                    docker compose ps
                '''
                sh '''
                    for i in $(seq 1 20); do
                      if curl -fsS "http://localhost:$PROXY_PORT/actuator/health" | grep -q '"status":"UP"'; then
                        echo "Health check passed"
                        curl -fsS "http://localhost:$PROXY_PORT/api/v1/add?a=2&b=3"; echo
                        exit 0
                      fi
                      echo "Waiting for the service to become healthy ($i/20)..."
                      sleep 3
                    done
                    echo "Service did not become healthy" >&2
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            sh 'docker image prune -f || true'
        }
        always {
            emailext(
                to: '$DEFAULT_RECIPIENTS',
                subject: "${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                mimeType: 'text/html',
                body: """
                    <p>Build <b>${env.JOB_NAME} #${env.BUILD_NUMBER}</b> finished with status
                       <b>${currentBuild.currentResult}</b>.</p>
                    <p>Image tag: <code>${env.IMAGE_NAME}:${env.IMAGE_TAG}</code></p>
                    <p>Console output: <a href="${env.BUILD_URL}console">${env.BUILD_URL}console</a></p>
                """
            )
        }
    }
}
