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
USER financeiro
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
