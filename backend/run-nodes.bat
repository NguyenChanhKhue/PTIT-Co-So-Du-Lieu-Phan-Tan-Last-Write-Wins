@echo off
setlocal
title Master-Master LWW Customer Address System
echo ========================================
echo   Master-Master Replication with LWW
echo   Customer Address Management
echo   Demonstrating Clock Skew Danger
echo ========================================
echo.

echo Building project...
call mvn clean package -DskipTests

if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b %errorlevel%
)

echo.
echo Creating data directory...
if not exist "data" mkdir data

set JAR_NAME=target\distributed-last-write-wins-0.0.1-SNAPSHOT.jar
if not exist "%JAR_NAME%" (
    echo Jar file not found: %JAR_NAME%
    pause
    exit /b 1
)

echo.
echo ========================================
echo Starting Node 1 (Port 8081)
echo Clock: NORMAL (0 seconds skew)
echo This node has ACCURATE time
echo ========================================
start "Node1-Normal" java -jar "%JAR_NAME%" --spring.config.additional-location=src/main/resources/config/application-node1.yml

timeout /t 2 /nobreak > nul

echo.
echo ========================================
echo Starting Node 2 (Port 8082)
echo Clock: FAST (+30 seconds skew)
echo WARNING: This node thinks it is 30 seconds in the future!
echo ========================================
start "Node2-Fast" java -jar "%JAR_NAME%" --spring.config.additional-location=src/main/resources/config/application-node2.yml

timeout /t 2 /nobreak > nul

echo.
echo ========================================
echo Starting Node 3 (Port 8083)
echo Clock: SLOW (-15 seconds skew)
echo WARNING: This node thinks it is 15 seconds in the past!
echo ========================================
start "Node3-Slow" java -jar "%JAR_NAME%" --spring.config.additional-location=src/main/resources/config/application-node3.yml

echo.
echo ========================================
echo All 3 nodes started successfully!
echo ========================================
echo.
echo API Endpoints:
echo Node 1 (Normal): http://localhost:8081/api/customer/C001
echo Node 2 (Fast):   http://localhost:8082/api/customer/C001
echo Node 3 (Slow):   http://localhost:8083/api/customer/C001
echo.
echo Frontend: http://localhost:3000
echo.
echo ========================================
echo DEMO SCENARIO (Clock Skew Danger):
echo ========================================
echo 1. Update customer on Node 2 (fast clock)
echo 2. Update SAME customer on Node 1 (normal clock) 1 second later
echo 3. The Node 1 update will be DISCARDED because Node 2's timestamp is larger
echo 4. This shows how a NEWER update can be lost due to clock skew!
echo.
echo Press any key to stop all nodes...
pause > nul

echo Stopping all nodes...
taskkill /F /IM java.exe /T > nul 2>&1
echo Done!
