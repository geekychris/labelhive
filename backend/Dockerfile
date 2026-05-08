# Stage 1: Build with Maven (Java 21 toolchain).
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests -B package

# Stage 2: Slim JRE runtime.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/data-label-*.jar app.jar
EXPOSE 8080
# Spring profile + DB connection are passed via env (see k8s/20-backend.yaml).
ENTRYPOINT ["java", "-jar", "app.jar"]
