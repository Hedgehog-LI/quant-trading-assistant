FROM mirror.gcr.io/library/eclipse-temurin:17-jre

WORKDIR /app

COPY target/quant-trading-assistant-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
