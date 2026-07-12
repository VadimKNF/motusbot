# Этап сборки: используем образ с предустановленным Gradle и Java 17
FROM gradle:8-jdk17-alpine AS build
WORKDIR /app
COPY . .
# Собираем проект через стандартный gradle (без ./gradlew)
RUN gradle bootJar -x test

# Этап запуска: минимальный образ только с JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# В Gradle готовый jar-файл лежит в папочке build/libs/
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-jar", "app.jar"]
