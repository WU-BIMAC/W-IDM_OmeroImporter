@ECHO OFF
set CLASSPATH=.
set CLASSPATH=%CLASSPATH%;"./dependency-jars/"

java -jar OmeroImporter-1.0.0.jar -cfg
PAUSE