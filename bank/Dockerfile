# syntax=docker/dockerfile:1
# Digital Bank - Java 21 / Spring Boot 3 executable jar on Eclipse Temurin 21.
# Build context is the repository root:  docker build -f bank/Dockerfile -t digisic/digitalbank:java21 .

FROM maven:3.9.9-eclipse-temurin-21 AS build
# Optional Maven Central mirror (e.g. https://maven-central.storage-download.googleapis.com/maven2)
ARG MAVEN_MIRROR_URL=
WORKDIR /build
COPY pom.xml ./
COPY bank/pom.xml bank/
COPY credit/pom.xml credit/
COPY bank/src bank/src
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    if [ -n "$MAVEN_MIRROR_URL" ]; then \
      printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>' \
        "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; \
    fi \
 && mvn -B -q -pl bank -am -DskipTests package \
 && cp bank/target/digitalbank-*.jar /build/digitalbank.jar

FROM eclipse-temurin:21-jre
RUN groupadd -r -g 1001 digital && useradd -r -u 1001 -g digital -d /app digital
WORKDIR /app
COPY --from=build --chown=digital:digital /build/digitalbank.jar /app/digitalbank.jar
USER digital
# HTTPS (server.port in application.properties), context path /bank.
# Configuration: environment variables (e.g. IO_DIGISIC_CREDIT_HOST) or a mounted /app/config/application.properties.
EXPOSE 8443
ENV JAVA_OPTS=""
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsk https://localhost:8443/bank/api/v1/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/digitalbank.jar \"$@\"", "--"]
