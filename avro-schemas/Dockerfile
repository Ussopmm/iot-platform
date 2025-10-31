# STAGE 1. BUILD
FROM gradle:8.14.2-jdk24 AS build

ARG NEXUS_URL=http://host.docker.internal:8082/repository/maven-snapshots
ARG NEXUS_USERNAME=admin
ARG NEXUS_PASSWORD=admin

ENV NEXUS_URL=${NEXUS_URL}
ENV NEXUS_USERNAME=${NEXUS_USERNAME}
ENV NEXUS_PASSWORD=${NEXUS_PASSWORD}

WORKDIR /home/gradle/src

COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src ./src
RUN gradle build --no-daemon -x test
RUN gradle publish --info



# STAGE 2. RUN
FROM openjdk:24-jdk-slim

RUN groupadd -r avro_user && useradd -r -g avro_user avro_user

RUN apt-get update && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /home/gradle/src/build/libs/*.jar .

RUN chown -R avro_user /app

USER avro_user

ENTRYPOINT ["java", "-jar", "avro-schemas-1.0.0-SNAPSHOT.jar"]
