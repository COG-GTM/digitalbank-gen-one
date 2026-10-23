# Build context is the repository root (the bank POM inherits from ../pom.xml):
#   docker build -f bank/Dockerfile --build-arg GIT_SHA=$(git rev-parse HEAD) -t digitalbank:$(git rev-parse --short HEAD) .

FROM eclipse-temurin:11-jdk AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY bank/mvnw bank/pom.xml bank/
COPY bank/.mvn bank/.mvn
COPY bank/src bank/src
WORKDIR /workspace/bank
ARG BUILD_NUMBER=12
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B package -Dmaven.test.skip=true -DbuildNumber=${BUILD_NUMBER}

FROM tomcat:9.0.48-jdk11-adoptopenjdk-hotspot
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.source="https://github.com/COG-GTM/digitalbank-gen-one" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.title="digitalbank"
EXPOSE 8080
EXPOSE 8443
COPY --from=build /workspace/bank/target/bank##*.war /usr/local/tomcat/webapps/
COPY --from=build /workspace/bank/target/classes/application.properties /usr/local/tomcat/conf/digitalbank.properties
COPY --from=build /workspace/bank/target/classes/keystore/digisic.p12 /usr/local/tomcat/conf/digisic.p12
COPY --from=build /workspace/bank/target/classes/server.xml /usr/local/tomcat/conf/server.xml
HEALTHCHECK CMD curl -fail http://localhost:8080/bank/api/v1/health || exit 1
