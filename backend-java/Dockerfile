FROM gradle:8.9-jdk17 AS build
ARG MODULE
WORKDIR /workspace
COPY backend-java/ ./
RUN gradle --no-daemon :${MODULE}:bootJar

FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/build/libs/${MODULE}-1.0.0-SNAPSHOT.jar /app/app.jar
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
