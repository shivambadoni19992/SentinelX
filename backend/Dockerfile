# Multi-architecture friendly builder using the versioned Maven image.
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /app
# Copy the whole backend (all module POMs must be present for the reactor).
COPY . .
# Warm the local repo then build just the requested module.
RUN mvn -B -q -pl ${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
WORKDIR /app
COPY --from=build /app/${SERVICE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]