# 仅复制新构建的产物，避免基于旧后端镜像叠加第二份 JAR。
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10101 luminous \
    && useradd --uid 10101 --gid luminous --create-home luminous \
    && mkdir -p /app/uploads \
    && chown 10101:10101 /app/uploads
COPY --chown=10101:10101 app.jar /app/app.jar
USER 10101:10101
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
