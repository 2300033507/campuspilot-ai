# =====================================================================
# Multi-stage build. Keeps the final image small — a Maven build image
# carries the whole toolchain, and none of it is needed at runtime.
#
# The JDK is pinned to 21 because that is what the project compiles for
# (and what application.yml's parent expects). An unpinned image is a
# deploy that fails months later for no visible reason.
# =====================================================================

# ---- build ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Dependencies first. This layer is cached and only re-runs when
# pom.xml changes, so ordinary code changes skip the download entirely.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests run in CI, not in the container build. A deploy should not fail
# because a unit test broke on a machine nobody is watching.
RUN mvn -B clean package -DskipTests

# ---- run ------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Never run as root. A container process has no more privileges than it
# is given, and it should be given as few as it can run with.
RUN useradd --system --create-home spring
USER spring:spring

EXPOSE 8080

# Containers should exit rather than restart forever on a config error —
# the platform logs are more useful than a hot loop.
ENV JAVA_TOOL_OPTIONS="-Xmx512m"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]