# syntax=docker/dockerfile:1
# Build context: flight-aggregator/

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
# Dependencies in their own layer: they change far less often than the sources.
RUN ./mvnw -q -B dependency:go-offline
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home app
COPY --from=build /workspace/infrastructure/target/flight-aggregator-infrastructure-*.jar /app.jar
USER app
EXPOSE 8080
# Liveness, not /actuator/health: overall health includes Redis, whose outage only degrades this service.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fs http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app.jar"]
