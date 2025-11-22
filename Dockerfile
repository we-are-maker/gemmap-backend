FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x ./gradlew

COPY src src

RUN ./gradlew clean build -x test --no-daemon

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=dev

CMD ["java", "-jar", "build/libs/gemmap-0.0.1-SNAPSHOT.jar"]