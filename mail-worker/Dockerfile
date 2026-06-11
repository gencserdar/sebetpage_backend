# syntax=docker/dockerfile:1.4
# Multi-stage build for mail-worker.
# Stage 1 compiles only what this module needs (no proto-contracts, no
# common-lib — this service is intentionally standalone) so the layer cache
# is small and rebuilds are quick.
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
    mvn -B -pl mail-worker -am -DskipTests dependency:go-offline || true
COPY mail-worker/src mail-worker/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked,id=maven-repo \
    mvn-package.sh "mail-worker"

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/mail-worker/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
