FROM mirror.gcr.io/library/eclipse-temurin:17-jdk AS builder

WORKDIR /workspace

# Copy dependency descriptors first so Docker can reuse the Maven dependency cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests clean package

FROM mirror.gcr.io/library/eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/target/quant-trading-assistant-0.0.1-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/libs

EXPOSE 8080

ENV QTA_EXTRA_LOADER_PATH="/app/libs"

# PropertiesLauncher supports loader.path, allowing optional runtime jars in
# /app/libs without making unavailable vendor SDKs a compile-time dependency.
ENTRYPOINT ["sh", "-c", "exec java -Dloader.path=\"${QTA_EXTRA_LOADER_PATH}\" -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher"]
