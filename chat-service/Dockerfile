# syntax=docker/dockerfile:1.4
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY proto-contracts/pom.xml proto-contracts/pom.xml
COPY common-lib/pom.xml common-lib/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY community-service/pom.xml community-service/pom.xml
COPY chat-service/pom.xml chat-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY mail-worker/pom.xml mail-worker/pom.xml
COPY --chmod=755 build-support/mvn-package.sh /usr/local/bin/mvn-package.sh
RUN --mount=type=cache,target=/root/.m2,sharing=locked,id=maven-repo \
    echo "==> Prefetching Maven dependencies (may take a few minutes on first build)..." && \
    mvn -B -pl proto-contracts,common-lib,chat-service -am -DskipTests dependency:go-offline || true
COPY proto-contracts/src proto-contracts/src
COPY common-lib/src   common-lib/src
COPY chat-service/src chat-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked,id=maven-repo \
    mvn-package.sh "proto-contracts,common-lib,chat-service"

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/chat-service/target/*.jar /app/app.jar
EXPOSE 8104 9104
ENTRYPOINT ["java","-jar","/app/app.jar"]
