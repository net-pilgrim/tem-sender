# Multi-stage Dockerfile for Scaleway TEM Multitenant Mail Sender (Micronaut + Java 21)

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Gradle wrapper and build configuration
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle gradle.properties* ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code and build application jar
COPY src ./src
RUN ./gradlew assemble --no-daemon -x test

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for container healthcheck
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

# Copy built shadow jar from builder stage
COPY --from=builder --chown=appuser:appgroup /workspace/build/libs/mail-sender-*-all.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
