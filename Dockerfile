# Build the jar first: ./gradlew :ledger-app:bootJar
# Layers are extracted so that dependency layers are cached across application changes.
FROM eclipse-temurin:21-jre AS extract
WORKDIR /build
COPY ledger-app/build/libs/ledger-app-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system ledger && useradd --system --gid ledger ledger
USER ledger
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
