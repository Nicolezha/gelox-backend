#!/bin/sh
#
# Maven Start Up Bash script (mvnw)
#
set -e

# Locate the script's directory
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(pwd)}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

# Determine Java command
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# If wrapper jar does not exist, download it
if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
    WRAPPER_URL=$(grep wrapperUrl "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2)
    echo "Downloading Maven Wrapper from $WRAPPER_URL"
    if command -v curl > /dev/null; then
        curl -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" -f -L
    elif command -v wget > /dev/null; then
        wget "$WRAPPER_URL" -O "$MAVEN_WRAPPER_JAR"
    else
        echo "ERROR: curl or wget is required to download the Maven Wrapper"
        exit 1
    fi
fi

exec "$JAVACMD" \
  -classpath "$MAVEN_WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
