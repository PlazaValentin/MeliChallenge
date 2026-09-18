# Imagen del backend. Compila adentro usando el wrapper del repo, asi que el
# build de Docker y el local usan la misma version de Gradle
# (ver PLAN_ANEXO_OTEL.md, Decision 7). Quien clona solo necesita Docker.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# El wrapper y los archivos de build se copian primero: mientras no cambien,
# Docker reusa la capa que baja la distribucion de Gradle y las dependencias,
# y un cambio de codigo no obliga a bajar todo otra vez.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Usuario sin privilegios: nada de lo que hace la app necesita root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /src/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
