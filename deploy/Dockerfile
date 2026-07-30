FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system letchat && useradd --system --gid letchat --home-dir /app letchat

WORKDIR /app
COPY letchat_java/target/letchat-1.0.jar /app/app.jar
RUN mkdir -p /app/files && chown -R letchat:letchat /app

USER letchat
EXPOSE 7070 7071

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:InitialRAMPercentage=30", "-XX:+UseG1GC", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
