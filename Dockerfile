FROM openjdk:8-jre

WORKDIR /opt/shinyproxy
COPY target/*.jar shinyproxy.jar

CMD ["java", "-jar", "shinyproxy.jar"]
