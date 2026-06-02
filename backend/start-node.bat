@echo off
REM Start a single node by number (1,2,3)
if "%1"=="" (
  echo Usage: start-node.bat 1^|2^|3
  exit /b 1
)
set NODE=%1
set JAR_NAME=target\distributed-last-write-wins-0.0.1-SNAPSHOT.jar
if not exist "%JAR_NAME%" (
  echo Jar not found: %JAR_NAME%
  echo Run mvn package first or use the existing jar in target\
  exit /b 1
)

if "%NODE%"=="1" start "Node1" java -jar "%JAR_NAME%" --spring.config.additional-location=src/main/resources/config/application-node1.yml
if "%NODE%"=="2" start "Node2" java -jar "%JAR_NAME%" --spring.config.additional-location=src/main/resources/config/application-node2.yml
if "%NODE%"=="3" start "Node3" java -jar "%JAR_NAME%" --spring.config.additional-location=src/main/resources/config/application-node3.yml

echo Started node %NODE% (if jar and config are correct).
