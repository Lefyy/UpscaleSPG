FROM maven:3.9.10-eclipse-temurin-24 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:24-jre
WORKDIR /opt/upscale

COPY --from=build /build/target/UpscaleSPG-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /opt/upscale/app/images/processed

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]