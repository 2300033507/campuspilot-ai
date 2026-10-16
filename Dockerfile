# =====================================================================
# Render / Docker build for the Spring Boot API.
#
# WHY THIS FILE IS AT THE REPO ROOT
#   Render's API silently ignores dockerContext and dockerfilePath, so
#   those can only be set through the dashboard. Placing the Dockerfile
#   at the repo root means Render's DEFAULTS find it: build context is
#   the repo root, and the Dockerfile is ./Dockerfile.
#
#   One file at the root, no dashboard configuration, no silent drift
#   between what the dashboard says and what the repo contains.
#
#   The alternative — backend/Dockerfile with an explicit context — is
#   tidier by convention but needs a setting that cannot be automated,
#   and a setting that can be wrong is worse than a layout that cannot.
#
# Build context is the repo root, hence the backend/ prefixes below.
# =====================================================================

# ---- build ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy the POM first and resolve dependencies alone. This layer is cached
# and only re-runs when pom.xml changes, so an ordinary code change skips
# the whole download again.
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline

COPY backend/src ./src
# Tests do not run in the container build. A deploy should not fail
# because a unit test broke on a machine nobody is watching; tests run in
# CI, where a failure is visible to someone.
RUN mvn -B clean package -DskipTests

# ---- run ------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Never run as root. A container process gets no privileges it was not
# given, so it should be given as few as it can run with.
RUN useradd --system --create-home spring
USER spring:spring

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Xmx512m"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]