FROM openjdk:8-jre-stretch

EXPOSE 8080
EXPOSE 8081

WORKDIR /opt/shinyproxy

# Add the entry script to use `make enter` to enter the Docker container
ADD entry.sh /opt/
RUN chmod 755 /opt/entry.sh

# Start the .jar application immediately
CMD ["java", "-jar", "/app/target/shinyproxy-1.1.0.jar"]
