FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY . .

RUN mvn package

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /app/target/makeshift-1.0-SNAPSHOT.jar app.jar

EXPOSE 8000

CMD ["java", "-jar", "app.jar"]
