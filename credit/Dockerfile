# Build context is the repository root (the credit POM inherits from ../pom.xml):
#   docker build -f credit/Dockerfile --build-arg GIT_SHA=$(git rev-parse HEAD) -t digitalcredit:$(git rev-parse --short HEAD) .

FROM eclipse-temurin:11-jdk AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY credit/mvnw credit/pom.xml credit/
COPY credit/.mvn credit/.mvn
COPY credit/src credit/src
WORKDIR /workspace/credit
ARG BUILD_NUMBER=4
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B package -Dmaven.test.skip=true -DbuildNumber=${BUILD_NUMBER}

FROM tomcat:9.0.48-jdk11-adoptopenjdk-hotspot
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.source="https://github.com/COG-GTM/digitalbank-gen-one" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.title="digitalcredit"
EXPOSE 8080
EXPOSE 8443
COPY --from=build /workspace/credit/target/credit##*.war /usr/local/tomcat/webapps/
COPY --from=build /workspace/credit/target/classes/application.properties /usr/local/tomcat/conf/digitalcredit.properties
COPY --from=build /workspace/credit/target/classes/keystore/digisic.p12 /usr/local/tomcat/conf/digisic.p12
COPY --from=build /workspace/credit/target/classes/server.xml /usr/local/tomcat/conf/server.xml
HEALTHCHECK CMD curl -fail http://localhost:8080/credit/api/v1/health || exit 1
