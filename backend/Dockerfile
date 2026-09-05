# SmartCampus ERP - backend production image (scope 71).
#
# Multi-stage: Maven build -> minimal JRE 21 runtime. No secret is baked into the
# image at any stage - JWT_SECRET, DB_*, AI_API_KEY, SMTP_*, JUDGE0_* are all read
# from the environment at container start (see application.properties /
# application-prod.properties), never passed as a build arg and never written to
# a file inside the image.
#
# Build:
#   docker build -t smartcampus-backend -f backend/Dockerfile backend
# Run:
#   docker run -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod smartcampus-backend

# ---------------------------------------------------------------------------
# Stage 1: build the jar with Maven + JDK 21
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so dependency resolution is cached in its own layer and
# only re-runs when pom.xml actually changes, not on every source edit.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: run on a minimal JRE, as a non-root user
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S smartcampus && adduser -S smartcampus -G smartcampus
WORKDIR /app

COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar
RUN chown smartcampus:smartcampus app.jar

USER smartcampus
EXPOSE 8080

# Container-level health check hits the same unauthenticated actuator endpoint
# docker-compose.prod.yml's healthcheck and the README both reference.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
