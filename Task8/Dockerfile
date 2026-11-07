FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY supplier-service/pom.xml supplier-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY delivery-service/pom.xml delivery-service/pom.xml
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY . .
ARG MODULE
RUN mvn -B -ntp -DskipTests -pl ${MODULE} -am package
RUN cp /workspace/${MODULE}/target/${MODULE}-1.0.0.jar /workspace/app.jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
