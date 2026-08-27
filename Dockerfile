# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build stage
# The Maven wrapper is distributionType=only-script, so it would have to
# download Maven itself on every build. Using the maven image skips that and
# keeps the JDK out of the runtime image.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so editing a source file does not
# re-download the world on the next Render build. Best-effort: a miss here is
# fetched by the package step below, it must not fail the build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests clean package

# ------------------------------------------------------------- runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copied by glob: a version bump in pom.xml must not silently break the image.
COPY --from=build /build/target/*.jar app.jar

# Render injects PORT and routes to it; 8081 is only the local default and is
# what server.port falls back to in application.properties.
ENV PORT=8081
EXPOSE 8081

# MaxRAMPercentage keeps the heap inside the container limit - the default
# 25% wastes most of a 512 MB Render instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
