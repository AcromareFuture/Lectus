FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
ARG MODULE=gateway
WORKDIR /app
COPY --from=build /build/${MODULE}/target/*.jar /app/service.jar
RUN mkdir -p /app/data && chown -R 10001:10001 /app
USER 10001
ENTRYPOINT ["java","-jar","/app/service.jar"]
