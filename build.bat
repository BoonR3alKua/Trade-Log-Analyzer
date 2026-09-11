@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo JDK 11 or newer is required. Add its bin directory to PATH.
    exit /b 1
)
if not exist build\classes mkdir build\classes
if /i "%~1"=="test" goto test
javac --release 8 -encoding UTF-8 -d build\classes src\*.java
if errorlevel 1 exit /b 1
goto success
:test
javac --release 8 -encoding UTF-8 -d build\classes src\*.java tests\*.java
if errorlevel 1 exit /b 1
java -cp "%~dp0build\classes" RegressionTests
if errorlevel 1 exit /b 1
:success
echo Build successful.
