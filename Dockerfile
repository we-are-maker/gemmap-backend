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

ENV JAVA_OPTS="-Duser.timezone=Asia/Seoul"

CMD ["sh", "-c", "java $JAVA_OPTS -jar build/libs/gemmap-0.0.1-SNAPSHOT.jar"]