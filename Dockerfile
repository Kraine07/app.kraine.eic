FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /app

COPY eic/pom.xml .
COPY eic/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:25

WORKDIR /app

COPY --from=build /app/target/eic-0.0.1-SNAPSHOT.jar .

EXPOSE 3100

CMD ["java","-jar","eic-0.0.1-SNAPSHOT.jar"]