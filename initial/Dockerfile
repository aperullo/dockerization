FROM openjdk:8-jdk-alpine
COPY gs-rest-service-master/build/libs/gs-rest-service-0.1.0.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
