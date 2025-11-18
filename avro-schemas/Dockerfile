FROM eclipse-temurin:24-jdk

WORKDIR /app

# Копируем gradle wrapper
COPY gradlew .
COPY gradle gradle/

# Копируем файлы проекта
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src/

# Делаем gradlew исполняемым
RUN chmod +x ./gradlew

# НЕ публикуем здесь! Публикация произойдёт в runtime через command в docker-compose
