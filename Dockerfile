FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN adduser -D financeiro
COPY --from=build /app/target/financeiro-api-*.jar app.jar
# Copia o agente New Relic
COPY newrelic/newrelic.jar /app/newrelic/newrelic.jar
COPY newrelic/newrelic.yml /app/newrelic/newrelic.yml

# Envia os logs do agente para a saida do conteiner
ENV NEW_RELIC_LOG_FILE_NAME=STDOUT

USER financeiro
EXPOSE 8082
ENTRYPOINT ["java", "-javaagent:/app/newrelic/newrelic.jar", "-jar", "app.jar"]
