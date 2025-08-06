# Runtime-only Dockerfile for Spring Boot Order Service
# Expects pre-built JAR from CI pipeline
FROM eclipse-temurin:21-jre-alpine

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from build context
# Expects JAR to be built by CI pipeline and available in target/ directory
COPY target/*-spring-boot.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8080

# Add health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

# Add application metadata
LABEL maintainer="dev-team@example.com" \
      application="order-service" \
      version="1.0-SNAPSHOT" \
      description="Order processing and management service" \
      java.version="21" \
      spring.boot.version="3.x"

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]