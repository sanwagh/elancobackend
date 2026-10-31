# ---- Stage 1: build the jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Copy only what Maven needs to resolve dependencies first, so this slow layer
# is cached and only re-runs when pom.xml changes (not on every code change).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY src/ src/
# Tests already run in development / CI, so skip them to keep the image build fast.
RUN ./mvnw -q -DskipTests package

# ---- Stage 2: small runtime image (JRE only, no compiler or Maven) ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Don't run as root inside the container.
RUN useradd --system --create-home appuser && mkdir /app/data && chown appuser /app/data
USER appuser

COPY --from=build /build/target/elancobackend-*.jar app.jar

# The H2 database file lives in /app/data (see spring.datasource.url), so mount a volume there.
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
