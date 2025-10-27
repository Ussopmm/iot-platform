# STAGE 1. BUILD
FROM gradle:8.14.3-jdk-21-and-24 AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN gradle build --no-daemon -x test


# STAGE 2. RUN
FROM openjdk:24-jdk-slim

RUN groupadd -r iot_user && useradd -r -g iot_user iot_user

RUN apt-get update && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /home/gradle/src/build/libs/*.jar .

RUN chown -R iot_user /app

USER iot_user

ENTRYPOINT ["java", "-jar", "event-collector-service-0.0.1-SNAPSHOT.jar"]
