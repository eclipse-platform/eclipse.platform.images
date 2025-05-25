pipeline {
	options {
		timeout(time: 40, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr:'5'))
		disableConcurrentBuilds(abortPrevious: true)
	}
	agent {
		label "ubuntu-latest"
	}
	tools {
		maven 'apache-maven-latest'
		jdk 'temurin-jdk17-latest'
	}
	stages {
		stage('Build') {
			steps {
				wrap([$class: 'Xvnc', useXauthority: true]) {
					sh """
					mvn clean verify \
						--batch-mode \
						--fail-at-end \
						-Dmaven.repo.local=$WORKSPACE/.m2/repository
					"""
				}
			}
			post {
				always {
					publishIssues issues:[scanForIssues(tool: java()), scanForIssues(tool: mavenConsole())]
				}
			}
		}
	}
}
