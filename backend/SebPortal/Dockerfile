# ---- Stage 1: build native IBAN validator library (jammy, matches runtime glibc) ----
FROM ubuntu:22.04 AS native-build
RUN apt-get update && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /native
COPY native/ ./
RUN make lib

# ---- Stage 2: build Java application ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/SebPortal/pom.xml .
RUN mvn dependency:resolve -q
COPY backend/SebPortal/src ./src
RUN mvn package -DskipTests -q

# ---- Stage 3: runtime ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/seb-portal-1.0-SNAPSHOT.jar app.jar
COPY --from=native-build /native/build/lib/libiban.so /app/native/libiban.so
EXPOSE 8084
ENTRYPOINT ["java", "-Djna.library.path=/app/native", "-jar", "app.jar"]
