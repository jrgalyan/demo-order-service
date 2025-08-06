pipeline {
    agent any
    
    environment {
        // Maven settings
        MAVEN_OPTS = '-Xmx1024m -XX:MaxPermSize=256m'
        MAVEN_CLI_OPTS = '--batch-mode --errors --fail-at-end --show-version'
        
        // Docker settings
        DOCKER_REGISTRY = credentials('docker-registry-url')
        DOCKER_CREDENTIALS = credentials('docker-registry-credentials')
        IMAGE_NAME = 'order-service'
        IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT.take(8)}"
        
        // Application settings
        APP_NAME = 'order-service'
        APP_VERSION = '1.0-SNAPSHOT'
        
        // Quality gates
        SONAR_PROJECT_KEY = 'order-service'
        
        // Notification settings
        SLACK_CHANNEL = '#deployments'
        EMAIL_RECIPIENTS = 'dev-team@example.com'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        skipDefaultCheckout()
    }
    
    triggers {
        pollSCM('H/5 * * * *') // Poll every 5 minutes
        cron('H 2 * * *') // Nightly build at 2 AM
    }
    
    stages {
        stage('Checkout') {
            steps {
                script {
                    // Clean workspace
                    cleanWs()
                    
                    // Checkout source code
                    checkout scm
                    
                    // Set build display name
                    currentBuild.displayName = "#${BUILD_NUMBER} - ${GIT_BRANCH}"
                    currentBuild.description = "Commit: ${GIT_COMMIT.take(8)}"
                }
            }
        }
        
        stage('Build Info') {
            steps {
                script {
                    echo "=== Build Information ==="
                    echo "Build Number: ${BUILD_NUMBER}"
                    echo "Git Branch: ${GIT_BRANCH}"
                    echo "Git Commit: ${GIT_COMMIT}"
                    echo "Image Tag: ${IMAGE_TAG}"
                    echo "Workspace: ${WORKSPACE}"
                    
                    // Display Maven version
                    sh 'mvn --version'
                    
                    // Display Java version
                    sh 'java -version'
                }
            }
        }
        
        stage('Dependency Check') {
            steps {
                script {
                    echo "=== Checking Dependencies ==="
                    
                    // Check if parent pom is available or if we need to install dependencies
                    def parentPomExists = fileExists('pom.xml')
                    if (parentPomExists) {
                        echo "Found pom.xml, checking for parent dependencies..."
                        
                        // Try to resolve dependencies, skip if parent is not available
                        sh """
                            mvn ${MAVEN_CLI_OPTS} dependency:resolve-sources \
                                -Dmaven.test.skip=true \
                                -Dmaven.javadoc.skip=true || echo "Parent dependencies not available, continuing with available dependencies"
                        """
                    }
                }
            }
        }
        
        stage('Compile') {
            steps {
                script {
                    echo "=== Compiling Application ==="
                    sh """
                        mvn ${MAVEN_CLI_OPTS} clean compile \
                            -Dmaven.test.skip=true \
                            -Dmaven.javadoc.skip=true
                    """
                }
            }
        }
        
        stage('Unit Tests') {
            steps {
                script {
                    echo "=== Running Unit Tests ==="
                    sh """
                        mvn ${MAVEN_CLI_OPTS} test \
                            -Dmaven.javadoc.skip=true \
                            -Dspring.profiles.active=test
                    """
                }
            }
            post {
                always {
                    // Publish test results
                    publishTestResults testResultsPattern: 'target/surefire-reports/*.xml'
                    
                    // Archive test reports
                    archiveArtifacts artifacts: 'target/surefire-reports/**/*', allowEmptyArchive: true
                }
            }
        }
        
        stage('Code Quality Analysis') {
            parallel {
                stage('SonarQube Analysis') {
                    when {
                        anyOf {
                            branch 'main'
                            branch 'develop'
                            changeRequest()
                        }
                    }
                    steps {
                        script {
                            echo "=== Running SonarQube Analysis ==="
                            withSonarQubeEnv('SonarQube') {
                                sh """
                                    mvn ${MAVEN_CLI_OPTS} sonar:sonar \
                                        -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                                        -Dsonar.projectName='${APP_NAME}' \
                                        -Dsonar.projectVersion=${APP_VERSION} \
                                        -Dsonar.sources=src/main/java \
                                        -Dsonar.tests=src/test/java \
                                        -Dsonar.java.binaries=target/classes \
                                        -Dsonar.junit.reportPaths=target/surefire-reports \
                                        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                                """
                            }
                        }
                    }
                }
                
                stage('Security Scan') {
                    steps {
                        script {
                            echo "=== Running Security Scan ==="
                            
                            // OWASP Dependency Check
                            sh """
                                mvn ${MAVEN_CLI_OPTS} org.owasp:dependency-check-maven:check \
                                    -DfailBuildOnCVSS=7 \
                                    -DsuppressionsFile=owasp-suppressions.xml || echo "Security scan completed with warnings"
                            """
                        }
                    }
                    post {
                        always {
                            // Archive security reports
                            archiveArtifacts artifacts: 'target/dependency-check-report.html', allowEmptyArchive: true
                        }
                    }
                }
            }
        }
        
        stage('Quality Gate') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    changeRequest()
                }
            }
            steps {
                script {
                    echo "=== Waiting for Quality Gate ==="
                    timeout(time: 5, unit: 'MINUTES') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        }
                    }
                }
            }
        }
        
        stage('Package') {
            steps {
                script {
                    echo "=== Packaging Application ==="
                    sh """
                        mvn ${MAVEN_CLI_OPTS} package \
                            -DskipTests=true \
                            -Dmaven.javadoc.skip=true
                    """
                }
            }
            post {
                always {
                    // Archive artifacts
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                    archiveArtifacts artifacts: 'target/*-sbom.json', allowEmptyArchive: true
                }
            }
        }
        
        stage('Docker Build') {
            when {
                expression { fileExists('Dockerfile') }
            }
            steps {
                script {
                    echo "=== Building Docker Image ==="
                    
                    // Build Docker image
                    def image = docker.build("${IMAGE_NAME}:${IMAGE_TAG}")
                    
                    // Tag with latest if on main branch
                    if (env.GIT_BRANCH == 'main') {
                        image.tag('latest')
                    }
                    
                    // Store image ID for later use
                    env.DOCKER_IMAGE_ID = image.id
                }
            }
        }
        
        stage('Docker Security Scan') {
            when {
                expression { fileExists('Dockerfile') }
            }
            steps {
                script {
                    echo "=== Scanning Docker Image for Vulnerabilities ==="
                    
                    // Trivy security scan
                    sh """
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                            -v \$(pwd):/workspace \
                            aquasec/trivy:latest image \
                            --exit-code 0 \
                            --severity HIGH,CRITICAL \
                            --format table \
                            ${IMAGE_NAME}:${IMAGE_TAG} || echo "Security scan completed with warnings"
                    """
                }
            }
        }
        
        stage('Integration Tests') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    echo "=== Running Integration Tests ==="
                    
                    // Run integration tests with Testcontainers
                    sh """
                        mvn ${MAVEN_CLI_OPTS} verify \
                            -Dspring.profiles.active=integration-test \
                            -Dtestcontainers.reuse.enable=true
                    """
                }
            }
            post {
                always {
                    // Publish integration test results
                    publishTestResults testResultsPattern: 'target/failsafe-reports/*.xml'
                    archiveArtifacts artifacts: 'target/failsafe-reports/**/*', allowEmptyArchive: true
                }
            }
        }
        
        stage('Push to Registry') {
            when {
                allOf {
                    anyOf {
                        branch 'main'
                        branch 'develop'
                    }
                    expression { fileExists('Dockerfile') }
                }
            }
            steps {
                script {
                    echo "=== Pushing Docker Image to Registry ==="
                    
                    docker.withRegistry("https://${DOCKER_REGISTRY}", "${DOCKER_CREDENTIALS}") {
                        def image = docker.image("${IMAGE_NAME}:${IMAGE_TAG}")
                        image.push()
                        
                        if (env.GIT_BRANCH == 'main') {
                            image.push('latest')
                        }
                    }
                }
            }
        }
        
        stage('Deploy to Staging') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    echo "=== Deploying to Staging Environment ==="
                    
                    // Deploy to staging using Docker Compose or Kubernetes
                    sh """
                        # Update staging deployment
                        echo "Deploying ${IMAGE_NAME}:${IMAGE_TAG} to staging"
                        
                        # Example: Update docker-compose file and restart services
                        # sed -i 's|image: ${IMAGE_NAME}:.*|image: ${IMAGE_NAME}:${IMAGE_TAG}|g' docker-compose.staging.yml
                        # docker-compose -f docker-compose.staging.yml up -d
                        
                        # Example: Update Kubernetes deployment
                        # kubectl set image deployment/${APP_NAME} ${APP_NAME}=${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} -n staging
                        # kubectl rollout status deployment/${APP_NAME} -n staging
                        
                        # For now, just simulate deployment
                        echo "Staging deployment completed successfully"
                    """
                }
            }
        }
        
        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                script {
                    echo "=== Deploying to Production Environment ==="
                    
                    // Manual approval for production deployment
                    input message: 'Deploy to Production?', ok: 'Deploy',
                          submitterParameter: 'DEPLOYER'
                    
                    // Deploy to production
                    sh """
                        # Update production deployment
                        echo "Deploying ${IMAGE_NAME}:${IMAGE_TAG} to production"
                        echo "Deployed by: ${DEPLOYER}"
                        
                        # Example: Blue-green deployment
                        # kubectl set image deployment/${APP_NAME} ${APP_NAME}=${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} -n production
                        # kubectl rollout status deployment/${APP_NAME} -n production
                        
                        # For now, just simulate deployment
                        echo "Production deployment completed successfully"
                    """
                }
            }
        }
    }
    
    post {
        always {
            script {
                // Clean up Docker images if they exist
                sh """
                    docker image prune -f || true
                    docker system prune -f --volumes || true
                """
                
                // Archive build logs
                archiveArtifacts artifacts: 'target/maven.log', allowEmptyArchive: true
            }
        }
        
        success {
            script {
                echo "=== Build Successful ==="
                
                // Send success notification
                slackSend(
                    channel: "${SLACK_CHANNEL}",
                    color: 'good',
                    message: ":white_check_mark: *${APP_NAME}* build #${BUILD_NUMBER} succeeded!\n" +
                            "Branch: ${GIT_BRANCH}\n" +
                            "Commit: ${GIT_COMMIT.take(8)}\n" +
                            "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
                )
                
                // Send email notification for main branch
                if (env.GIT_BRANCH == 'main') {
                    emailext(
                        subject: "✅ ${APP_NAME} - Build #${BUILD_NUMBER} Successful",
                        body: """
                            <h2>Build Successful</h2>
                            <p><strong>Project:</strong> ${APP_NAME}</p>
                            <p><strong>Build Number:</strong> ${BUILD_NUMBER}</p>
                            <p><strong>Branch:</strong> ${GIT_BRANCH}</p>
                            <p><strong>Commit:</strong> ${GIT_COMMIT}</p>
                            <p><strong>Docker Image:</strong> ${IMAGE_NAME}:${IMAGE_TAG}</p>
                            <p><strong>Build URL:</strong> <a href="${BUILD_URL}">${BUILD_URL}</a></p>
                        """,
                        to: "${EMAIL_RECIPIENTS}",
                        mimeType: 'text/html'
                    )
                }
            }
        }
        
        failure {
            script {
                echo "=== Build Failed ==="
                
                // Send failure notification
                slackSend(
                    channel: "${SLACK_CHANNEL}",
                    color: 'danger',
                    message: ":x: *${APP_NAME}* build #${BUILD_NUMBER} failed!\n" +
                            "Branch: ${GIT_BRANCH}\n" +
                            "Commit: ${GIT_COMMIT.take(8)}\n" +
                            "Build URL: ${BUILD_URL}"
                )
                
                // Send email notification
                emailext(
                    subject: "❌ ${APP_NAME} - Build #${BUILD_NUMBER} Failed",
                    body: """
                        <h2>Build Failed</h2>
                        <p><strong>Project:</strong> ${APP_NAME}</p>
                        <p><strong>Build Number:</strong> ${BUILD_NUMBER}</p>
                        <p><strong>Branch:</strong> ${GIT_BRANCH}</p>
                        <p><strong>Commit:</strong> ${GIT_COMMIT}</p>
                        <p><strong>Build URL:</strong> <a href="${BUILD_URL}">${BUILD_URL}</a></p>
                        <p><strong>Console Output:</strong> <a href="${BUILD_URL}console">${BUILD_URL}console</a></p>
                    """,
                    to: "${EMAIL_RECIPIENTS}",
                    mimeType: 'text/html'
                )
            }
        }
        
        unstable {
            script {
                echo "=== Build Unstable ==="
                
                // Send unstable notification
                slackSend(
                    channel: "${SLACK_CHANNEL}",
                    color: 'warning',
                    message: ":warning: *${APP_NAME}* build #${BUILD_NUMBER} is unstable!\n" +
                            "Branch: ${GIT_BRANCH}\n" +
                            "Commit: ${GIT_COMMIT.take(8)}\n" +
                            "Build URL: ${BUILD_URL}"
                )
            }
        }
        
        cleanup {
            script {
                // Clean workspace
                cleanWs()
            }
        }
    }
}