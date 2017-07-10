#!/usr/bin/env bash

export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:bin/java::")            
/opt/apache-maven-3.5.0/bin/mvn -s settings.xml -U clean install rpm:rpm
