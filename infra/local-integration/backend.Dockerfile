FROM eclipse-temurin:25.0.3_9-jdk-noble AS build

WORKDIR /workspace

COPY backend/gradle ./gradle
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies

COPY backend/src ./src
RUN ./gradlew --no-daemon bootJar \
    && cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:25.0.3_9-jre-noble

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system pawcycle \
    && useradd --system --gid pawcycle --home-dir /app --shell /usr/sbin/nologin pawcycle

WORKDIR /app
COPY --from=build --chown=pawcycle:pawcycle /workspace/app.jar ./app.jar

USER pawcycle
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
