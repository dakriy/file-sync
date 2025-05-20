FROM gradle:jdk17 AS builder

WORKDIR /build
COPY . .

RUN ./gradlew shadowjar

FROM openjdk:24-jdk-slim

WORKDIR /app

COPY --from=builder /build/build/libs/*-all.jar /app/file-sync.jar

ENTRYPOINT ["java", "-jar", "/app/file-sync.jar"]
