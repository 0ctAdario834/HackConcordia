FROM maven:3.9-eclipse-temurin-27 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:27-jre
WORKDIR /app
COPY --from=build /src/target/app.jar app.jar
EXPOSE 7070
CMD ["java", "-jar", "app.jar"]