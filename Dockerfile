FROM gradle:jdk17 AS builder

WORKDIR /build
COPY . .

RUN ./gradlew installShadowDist

FROM openjdk:24-jdk-slim

# Install ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/build/install/file-sync-shadow /app

ENV PATH="/app/bin:$PATH"

ENTRYPOINT ["file-sync"]
