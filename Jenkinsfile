pipeline {

    agent {
        kubernetes {
            yamlFile 'kubernetesPod.yaml'
            workspaceVolume dynamicPVC(accessModes: 'ReadWriteOnce', requestsSize: '40Gi')
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }

    stages {

        stage('build and deploy to nexus'){

            steps {

                container('containerproxy-build') {

                     configFileProvider([configFile(fileId: 'maven-settings-rsb', variable: 'MAVEN_SETTINGS_RSB')]) {

                         sh 'mvn -B -s $MAVEN_SETTINGS_RSB -Dmaven.repo.local=/home/jenkins/agent/m2 -U clean deploy'

                     }
                }
            }
        }
    }
}
