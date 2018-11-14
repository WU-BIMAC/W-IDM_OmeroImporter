@ECHO OFF
set CLASSPATH=.
set CLASSPATH=%CLASSPATH%;"./dependency-jars/"

java -jar OmeroImporter.jar -cfg
PAUSE