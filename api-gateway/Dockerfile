FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY proto-contracts/pom.xml proto-contracts/pom.xml
COPY common-lib/pom.xml common-lib/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY group-service/pom.xml group-service/pom.xml
COPY chat-service/pom.xml chat-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY mail-worker/pom.xml mail-worker/pom.xml
RUN mvn -q -pl proto-contracts,common-lib,api-gateway -am -DskipTests dependency:go-offline || true
COPY proto-contracts/src proto-contracts/src
COPY common-lib/src  common-lib/src
COPY api-gateway/src api-gateway/src
RUN mvn -q -pl proto-contracts,common-lib,api-gateway -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/*.jar /app/app.jar
EXPOSE 8000
ENTRYPOINT ["java","-jar","/app/app.jar"]
