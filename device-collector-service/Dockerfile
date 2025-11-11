# STAGE 1. BUILD
FROM gradle:8.14.3-jdk-21-and-24 AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN gradle build --no-daemon


# STAGE 2. RUN
FROM openjdk:24-jdk-slim

RUN groupadd -r dcs_user && useradd -r -g dcs_user dcs_user

RUN apt-get update && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /home/gradle/src/build/libs/*.jar .

RUN chown -R dcs_user /app

USER dcs_user

ENTRYPOINT ["java", "-jar", "event-collector-service-0.0.1-SNAPSHOT.jar"]
