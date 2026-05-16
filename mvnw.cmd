@REM Maven Start Up Batch script (mvnw.cmd)
@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
if not "%MAVEN_BASEDIR%"=="" set MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%

set MAVEN_WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties

if not "%JAVA_HOME%"=="" (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVACMD=java
)

if not exist "%MAVEN_WRAPPER_JAR%" (
    powershell -Command "$props = Get-Content '%MAVEN_WRAPPER_PROPERTIES%' | Where-Object { $_ -match 'wrapperUrl' }; $url = ($props -split '=', 2)[1].Trim(); Invoke-WebRequest -Uri $url -OutFile '%MAVEN_WRAPPER_JAR%'"
)

"%JAVACMD%" -classpath "%MAVEN_WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*

endlocal
