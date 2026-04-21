# Этап 1: сборка JAR с Gradle и Java 17
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
# Копируем только файлы сборки для кэширования зависимостей
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon
# Копируем исходники и собираем JAR
COPY src ./src
RUN gradle bootJar --no-daemon

# Этап 2: запуск приложения
FROM amazoncorretto:17
WORKDIR /app
COPY --from=builder /app/build/libs/kafka-to-postgres-app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]