# STAGE 1. BUILD
FROM gradle:8.14.3-jdk-21-and-24 AS build

# Accept build arguments for Nexus credentials
ARG NEXUS_URL
ARG NEXUS_USERNAME
ARG NEXUS_PASSWORD

# Set them as environment variables for Gradle
ENV NEXUS_URL=${NEXUS_URL}
ENV NEXUS_USERNAME=${NEXUS_USERNAME}
ENV NEXUS_PASSWORD=${NEXUS_PASSWORD}

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN gradle build --no-daemon -x test


# STAGE 2. RUN
FROM eclipse-temurin:24-jdk

RUN groupadd -r dcs_user && useradd -r -g dcs_user dcs_user

RUN apt-get update && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /home/gradle/src/build/libs/*.jar .

RUN chown -R dcs_user /app

USER dcs_user

ENTRYPOINT ["java", "-jar", "device-collector-service-0.0.1-SNAPSHOT.jar"]
