# CredChain backend (Spring Boot, Java 21)
#   docker build -t credchain-backend backend    builds the image (tests run in CI and on your laptop, not here)
# docker compose --profile app (infra/docker-compose.yml) builds and starts it with the rest of the app.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
# Dependencies first, so they are cached until pom.xml changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package \
 && cp target/backend-*.jar app.jar

FROM eclipse-temurin:21-jre AS runtime
# Run as a normal user, not root
RUN useradd --system --uid 10001 --home-dir /app credchain
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER credchain
EXPOSE 8080
# Use up to 75% of the container's memory; /actuator/health/liveness only says "the app is running",
# so a slow blockchain RPC or storage never makes the container restart
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
