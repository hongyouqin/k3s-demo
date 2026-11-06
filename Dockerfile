# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy the POM first so dependency resolution is cached independently of source changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S -g 10001 app \
    && adduser -S -u 10001 -G app -h /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/k3s-demo-*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
