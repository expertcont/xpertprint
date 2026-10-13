FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/quarkus-app/ /app/
RUN mkdir -p /data/pdfs
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/quarkus-run.jar"]
