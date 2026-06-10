# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:resolve dependency:resolve-plugins

COPY src ./src
RUN mvn -B -q package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN useradd --system --uid 10001 appuser \
    && mkdir -p /app/src/main/logs \
    && chown -R appuser:appuser /app

USER appuser

COPY --chown=appuser:appuser --from=build /app/target/dejebu-server-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
