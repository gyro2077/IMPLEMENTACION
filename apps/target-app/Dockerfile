# Etapa de build para no depender de Maven en el host.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Imagen final liviana para ejecución.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends wget \
	&& rm -rf /var/lib/apt/lists/*

# Agente OTel para instrumentacion automatica de trazas.
RUN mkdir -p /opt/otel \
	&& wget -q -O /opt/otel/opentelemetry-javaagent.jar \
	https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.26.1/opentelemetry-javaagent.jar

COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
