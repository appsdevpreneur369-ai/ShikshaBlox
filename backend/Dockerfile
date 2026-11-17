# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true \
 && java -Djarmode=tools -jar target/shikshablox.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-alpine
# cwebp/dwebp for WebP image variants
RUN apk add --no-cache libwebp-tools && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=10 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
