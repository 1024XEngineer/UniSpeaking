FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/unispeaking_server-0.0.1-SNAPSHOT.jar /app/unispeaking-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/unispeaking-server.jar"]
