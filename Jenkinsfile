pipeline {

    agent {
        kubernetes {
            yamlFile 'kubernetesPod.yaml'
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
                         
                         sh 'mvn -s $MAVEN_SETTINGS_RSB -U clean install deploy'
                         
                     }
                }
            }
        }
    }
}
