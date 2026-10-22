#!/bin/sh
# Gradle wrapper bootstrap; official wrapper distribution is downloaded on first use.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
