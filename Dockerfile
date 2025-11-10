FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build
COPY . .

RUN ./gradlew installShadowDist

FROM eclipse-temurin:25-jre-alpine

# Install ffmpeg
RUN apk add --no-cache ffmpeg

WORKDIR /app

COPY --from=builder /build/build/install/file-sync-shadow /app

ENV PATH="/app/bin:$PATH"

ENTRYPOINT ["file-sync"]
