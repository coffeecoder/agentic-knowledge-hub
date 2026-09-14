FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 appuser && useradd --uid 10001 --gid 10001 --create-home appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /workspace/target/agentic-knowledge-hub-0.1.0.jar app.jar
USER appuser
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
