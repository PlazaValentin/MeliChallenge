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

# Agente de OpenTelemetry, con la version pineada: un agente que se actualiza
# solo cambiaria la telemetria sin que cambie una linea de codigo.
ARG OTEL_AGENT_VERSION=2.31.1
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Usuario sin privilegios: nada de lo que hace la app necesita root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app \
 && chmod 644 /app/opentelemetry-javaagent.jar
COPY --from=build --chown=app:app /src/build/libs/*.jar app.jar
USER app

# La identidad del servicio viaja con el artefacto y no con el despliegue, para
# que la imagen no sea anonima si alguien la corre suelta; el deployment puede
# sobreescribirla (ver PLAN_ANEXO_OTEL.md, Decision 8).
ENV OTEL_SERVICE_NAME=seller-dashboard-api

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
