FROM tomcat:9.0.48-jdk11-adoptopenjdk-hotspot
# TLS keystore is mounted at runtime to /usr/local/tomcat/conf/digisic.p12 and its
# password is read from the DIGISIC_KEYSTORE_PASSWORD environment variable by server.xml
ENV CATALINA_OPTS="-Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource"
EXPOSE 8080
EXPOSE 8443
COPY /target/credit##2.1.0.4.war /usr/local/tomcat/webapps/
COPY /target/classes/application.properties /usr/local/tomcat/conf/digitalcredit.properties
COPY /target/classes/server.xml /usr/local/tomcat/conf/server.xml
# conf/ is often a persistent volume; refresh server.xml on every start so an old
# copy with a hard-coded keystore password is never reused after an upgrade
COPY /target/classes/server.xml /usr/local/tomcat/digisic-server.xml
CMD ["sh", "-c", "cp /usr/local/tomcat/digisic-server.xml /usr/local/tomcat/conf/server.xml && exec catalina.sh run"]
HEALTHCHECK CMD curl -fail http://localhost:8080/credit/api/v1/health || exit 1