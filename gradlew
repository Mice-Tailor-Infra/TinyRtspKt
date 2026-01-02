#!/bin/sh
APP_HOME=$( cd "$( dirname "$0" )" && pwd )
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
java -Dorg.gradle.appname=gradlew -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
