# ─────────────────────────────────────────────
# STAGE 1: BUILD
# Uses a full JDK image to compile the code.
# This stage produces the JAR file.
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy the Maven wrapper first — this is a Docker layer caching trick.
# If pom.xml files don't change, Docker reuses the cached dependency
# download layer and skips re-downloading on every build. Saves minutes.
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./

# Copy ALL pom.xml files before any source code
COPY pom.xml ./
COPY api/pom.xml api/
COPY sdk/pom.xml sdk/

# Copy SDK source and install it into the container's local Maven repo.
# The SDK is a local module — it doesn't exist on Maven Central.
# We must build and install it FIRST, otherwise when the api module tries
# to resolve featureflag-sdk as a dependency, Maven can't find it anywhere.
COPY sdk/src sdk/src
RUN ./mvnw install -pl sdk -am -DskipTests -q

# Now download the API's external dependencies.
# This layer is cached as long as pom.xml files don't change —
# so repeated builds are fast when you only change source code.
RUN ./mvnw dependency:resolve -pl api -q

# Copy API source code (this layer rebuilds when you change your Java files)
COPY api/src api/src

# Build the final JAR — skip tests (tests run in the CI pipeline, not here)
RUN ./mvnw package -pl api -DskipTests -q

# ─────────────────────────────────────────────
# STAGE 2: RUNTIME
# Uses a minimal JRE image — no compiler, no build tools.
# This is what actually runs in production.
# Result: image is ~200MB instead of ~600MB
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Security best practice: never run as root inside a container.
# Create a dedicated non-root user and group for the application.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy ONLY the compiled JAR from the build stage.
# Nothing else from the build stage crosses into this image.
COPY --from=build /app/api/target/*.jar app.jar

# Tell Docker this container listens on port 8080
# (documentation only — does not actually open the port)
EXPOSE 8080

# Health check using Spring Actuator.
# Docker uses this to know when the container is truly ready.
# start_period=60s gives Spring Boot time to start before checks begin.
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Switch to the non-root user before starting the app
USER appuser

# Start the application.
# SPRING_PROFILE env var comes from docker-compose.yml environment section.
ENTRYPOINT ["java", \
  "-jar", \
  "-Dspring.profiles.active=${SPRING_PROFILE:-prod}", \
  "app.jar"]