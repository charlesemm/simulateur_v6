# Image du simulateur (Podman). Construction en deux etapes : compiler, puis ne garder que le JAR en couches.
# On utilise le Maven de l'image (et non mvnw) pour eviter tout souci de fins de ligne venant de Windows.

FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package \
    && java -Djarmode=tools -jar target/simulateur.jar extract --layers --destination target/extracted

FROM docker.io/library/eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 simulateur && adduser -S -u 10001 -G simulateur simulateur
WORKDIR /app
COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./
RUN mkdir -p /app/scenarios && chown -R simulateur:simulateur /app
USER simulateur
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/simulateur.jar"]
