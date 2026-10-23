# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Digital Broker - Apache ActiveMQ Artemis 2.31.x on Eclipse Temurin 21
# Build: docker build -t digisic/digitalbroker:2.31.2 broker

FROM eclipse-temurin:21-jre
LABEL maintainer="Apache ActiveMQ Team"
# Make sure pipes are considered to determine success, see: https://github.com/hadolint/hadolint/wiki/DL4006
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG ARTEMIS_VERSION=2.31.2
ARG ARTEMIS_SHA512=b1f2a9fc245db400982bed0fd8df0f5b1c25a42aa7170bda7b02e37ea92d1f59fd4a290bd18e3b47f68735a0524cfc239db2a01838927c33a0fb654cacf8f095
# Optional Maven Central mirror tried first (Artemis is published as org.apache.activemq:apache-artemis:bin);
# falls back to the Apache release archive.
ARG MAVEN_MIRROR_URL=

# The Digital Bank / Digital Credit apps connect with admin/admin (spring.artemis.user / spring.artemis.password)
ENV ARTEMIS_USER=admin
ENV ARTEMIS_PASSWORD=admin
ENV CREATE_ARGUMENTS="--silent --require-login --http-host 0.0.0.0 --relax-jolokia"

# add user and group for artemis - keep UID/GID 1000 of the legacy image so existing instance volumes stay writable
# (the temurin base ships an 'ubuntu' user on 1000)
RUN (userdel -r ubuntu 2>/dev/null || true) && (groupdel ubuntu 2>/dev/null || true) \
 && groupadd -g 1000 -r artemis && useradd -r -u 1000 -g artemis -d /var/lib/artemis-instance artemis

# Download Apache Artemis (checksum verified)
WORKDIR /opt
RUN urls="https://archive.apache.org/dist/activemq/activemq-artemis/${ARTEMIS_VERSION}/apache-artemis-${ARTEMIS_VERSION}-bin.tar.gz" \
 && if [ -n "${MAVEN_MIRROR_URL}" ]; then \
      urls="${MAVEN_MIRROR_URL%/}/org/apache/activemq/apache-artemis/${ARTEMIS_VERSION}/apache-artemis-${ARTEMIS_VERSION}-bin.tar.gz ${urls}"; \
    fi \
 && for url in ${urls}; do echo "downloading ${url}"; curl -fsSL --retry 3 -o amq.tar.gz "${url}" && break; rm -f amq.tar.gz; done \
 && echo "${ARTEMIS_SHA512}  amq.tar.gz" | sha512sum -c - \
 && tar -xzf amq.tar.gz \
 && rm amq.tar.gz \
 && mv "apache-artemis-${ARTEMIS_VERSION}" activemq-artemis

# Web Server
EXPOSE 8161 \
# JMX Exporter
    9404 \
# Port for CORE,MQTT,AMQP,HORNETQ,STOMP,OPENWIRE
    61616 \
# Port for HORNETQ,STOMP
    5445 \
# Port for AMQP
    5672 \
# Port for MQTT
    1883 \
#Port for STOMP
    61613

RUN mkdir /var/lib/artemis-instance && chown -R artemis:artemis /var/lib/artemis-instance

COPY --chmod=755 docker-run.sh /

USER artemis

# Expose some outstanding folders
VOLUME ["/var/lib/artemis-instance"]
WORKDIR /var/lib/artemis-instance

HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=6 \
  CMD bash -c '</dev/tcp/127.0.0.1/61616' || exit 1

ENTRYPOINT ["/docker-run.sh"]
CMD ["run"]
