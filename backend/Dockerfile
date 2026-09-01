# Production Dockerfile for Luminous Backend (Spring Boot 3.5.0 + Java 21)
FROM eclipse-temurin:21-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S luminous && adduser -S luminous -G luminous

# Create upload directory
RUN mkdir -p /app/uploads && chown -R luminous:luminous /app

# Copy built artifact
COPY target/luminous-api-*.jar /app/app.jar

USER luminous
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
