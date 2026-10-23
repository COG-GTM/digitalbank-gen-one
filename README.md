# Digital Bank (Generation One)

Digital Bank is a sample application that was developed for exploratory purposes to examine development frameworks and techniques. The application is an example online digital banking application with integrations into other services.

## Getting Started

* Using either an Eclipse IDE or IntelliJ, import the source code as an Existing Maven Project. 

## Prerequisites

* Install [Java SDK 21](https://adoptium.net/temurin/releases/?version=21) (Eclipse Temurin or any OpenJDK 21 build)
* Install [Apache Maven](https://maven.apache.org/install.html) 3.9+
* (Optional) Install [Docker](https://docs.docker.com/get-docker/) with the Compose v2 plugin to run the full estate
* Install [Eclipse IDE](https://www.eclipse.org/ide/), [Spring Tools Suite](https://spring.io/tools) or [IntelliJ](https://www.jetbrains.com/idea/)

## Executing Tests

* JUnit Tests - Execute "mvn clean test"
* Serenity BDD Acceptance Tests - Execute "mvn clean verify"
  * Serenity BDD tests use Cucumber Feature file definitions. 
    * UI Tests are executed using the Selenium Java framework.
    * API Test are executed using the Rest Assured Java framework.
  * Use '-Dcucumber.options="--tags {tag}"' maven command line option to filter test execution based on Feature or Scenario tags defined within the [feature files](src/test/resources/features).

## Packaging

* Build with Java 21: execute "mvn clean package" (add "-DbuildNumber={###}" to stamp a build number). The output is an executable Spring Boot jar, bank/target/digitalbank-{version}.jar.
* Run it locally: "java -jar bank/target/digitalbank-{version}.jar" serves HTTPS on 8443 with context path /bank.
  * Override any setting with command line properties (e.g. "--spring.artemis.broker-url=tcp://localhost:61616"), environment variables (e.g. "SPRING_ARTEMIS_BROKER_URL") or an application.properties file in ./config next to the jar.
* To create a Docker image (eclipse-temurin:21-jre running the executable jar, built in a multi-stage Maven build), execute from the repository root: "docker build -f bank/Dockerfile -t {imageName}:{imageTag} ."
  * Set "--build-arg MAVEN_MIRROR_URL=..." to resolve dependencies through a Maven Central mirror.

## Deployment

Digital Bank can be deployed as a single standalone application service or be deployed and configured to integrate with Digital Credit.

* Executable Jar
  * "java -jar bank/target/digitalbank-{version}.jar" (Java 21). The previous Tomcat WAR deployment (digitalbank.properties watched in ${catalina.base}/conf) is replaced by standard Spring Boot externalized configuration.
* Docker
  * "docker run -p 8443:8443 {imageName}:{imageTag}"; the container health check calls https://localhost:8443/bank/api/v1/health.
* Docker Compose (full estate: broker + credit + bank) - see the [top-level docker-compose.yml](docker-compose.yml) below.
* Kubernetes - manifests under [deploy/k8s](deploy/k8s).

## Docker Compose Deployment

The whole estate - Digital Broker (Apache Artemis 2.31), Digital Credit and Digital Bank, all on Java 21 (eclipse-temurin:21-jre) - is defined in one [docker-compose.yml](docker-compose.yml). Images are built from source (multi-stage Maven build), so no pre-built images are needed.

* Start (H2 in-memory database, default): "docker compose up -d --build" or "./digitalbank.sh up"
  * Bank: https://localhost:8443/bank
  * Credit: https://localhost:3500/credit
  * Broker: tcp://localhost:61616 (core/JMS), console http://localhost:8161 (admin/admin); queues CREDIT.APP.REQUEST / CREDIT.APP.RESPONSE
* Database variants are overlays on the same file:
  * [MySQL](deploy/docker-compose/docker-compose-mysql.yml): "docker compose -f docker-compose.yml -f deploy/docker-compose/docker-compose-mysql.yml up -d --build" or "./digitalbank.sh up mysql"
  * [PostgreSQL](deploy/docker-compose/docker-compose-postgres.yml): "./digitalbank.sh up postgres" (PostgreSQL 16; [postgres-init](deploy/docker-compose/postgres-init/) hands schema "public" to "digitaluser")
  * [MS SQL Server](deploy/docker-compose/docker-compose-mssql.yml): "./digitalbank.sh up mssql" (databases are initialised ~90 seconds after SQL Server starts)
* Stop and remove containers/volumes: "./digitalbank.sh down [mysql|postgres|mssql]"
* Set MAVEN_MIRROR_URL (e.g. https://maven-central.storage-download.googleapis.com/maven2) to build through a Maven Central mirror.
* The bank calls the external ATM / VISA / Open Banking services (bankingservices.io, creditservices.io, openbankingservices.io). To run against the recorded fixtures instead, add the [parity overlay](deploy/docker-compose/docker-compose-parity.yml) ("./digitalbank.sh up --parity"), which starts the fixture mock on 127.0.0.1:8099; tools/parity/replay.py can then run against https://localhost:8443/bank and https://localhost:3500/credit.

## Kubernetes Deployment

Manifests for the Java 21 images live under [deploy/k8s](deploy/k8s) (namespace digibank, services digital-bank-svc:8443, digital-bank-credit-svc:3500, digital-bank-broker-svc:61616/8161, Traefik ingress). Build and push the images to a registry your cluster can pull from, then run deploy/k8s/digitalbank/deploy-digibank.sh.

## Interfaces

* Web Interface @ https://{hostname}:{port}/bank
  * Sample user data is created when the application is started. User credentials for these users are as follows:
    * jsmith@demo.io/Demo123!
    * nsmith@demo.io/Demo123!
* Swagger UI @ https://{hostname}:{port}/bank/swagger-ui.html
  * The API Admin user credentials are as follows:
    * admin@demo.io/Demo123!

## Built With

* [Sufee Admin Dashboard](https://github.com/rockmantic2018/sufee-admin-dashboard-master) - A HTML5 Dashboard Template
* [Spring Boot](https://spring.io/projects/spring-boot) - Application Framework
* [Thymeleaf](https://www.thymeleaf.org/) - Server-Side Java Template
* [Swagger](https://swagger.io/) - API Documentation
* [Apache Maven](https://maven.apache.org/) - Software Project Management
* [Apache Tomcat](http://tomcat.apache.org/) - Embedded Servlet Engine
* [Eclipse Temurin](https://adoptium.net/) - Java 21 Runtime (container images)
* [Apache Artemis](https://activemq.apache.org/components/artemis/) - Messaging Broker
* [H2 Database Engine](https://www.h2database.com/html/main.html) - In-Memory Database
* [MS SQL Server Database Engine](https://www.microsoft.com/en-us/sql-server/default.aspx) - Microsoft SQL Server
* [MySQL Database Engine](https://www.mysql.com/) - MySQL 
* [PostgreSQL Database Engine](https://www.postgresql.org/) - PostgreSQL
* [Serenity BDD](http://www.thucydides.info/#/) - Acceptance Testing Framework
* [JUnit 5](https://junit.org/junit5/) - Unit Testing Framework
* [CodeSV](http://codesv.io/) - Unit Testing with Mock Services
* [BlazeMeter](https://www.blazemeter.com/) - Functional / Performance API & UI Testing Services


## Authors

[Mark Asbury](https://github.com/asburymr)

See the additional list of [contributors](https://github.com/digisic/Digital-Bank/graphs/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
