# syntax=docker/dockerfile:1.4
# Multi-stage build for auth-service.
# Stage 1 compiles the whole reactor (the parent + shared modules + this service)
# so Maven can resolve proto-contracts and common-lib from the local repo.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Copy only poms first so dependency resolution can cache between builds.
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
    mvn -B -pl proto-contracts,common-lib,auth-service -am -DskipTests dependency:go-offline || true
# Then the source.
COPY proto-contracts/src proto-contracts/src
COPY common-lib/src    common-lib/src
COPY auth-service/src  auth-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked,id=maven-repo \
    mvn-package.sh "proto-contracts,common-lib,auth-service"

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/auth-service/target/*.jar /app/app.jar
EXPOSE 8101 9101
ENTRYPOINT ["java","-jar","/app/app.jar"]
