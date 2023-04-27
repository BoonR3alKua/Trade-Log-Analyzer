@echo off
REM Build script for Trade Log Analyzer & Alert Engine
REM Usage: build.bat or .\build.bat

setlocal enabledelayedexpansion

echo ======================================================================
echo Trade Log Analyzer - Build Script
echo ======================================================================

REM Check if Java is installed. Fall back to the installed JDK location.
set "JAVAC="
for /f "delims=" %%J in ('where javac 2^>nul') do if not defined JAVAC set "JAVAC=%%J"
if not defined JAVAC set "JAVAC=C:\Program Files\Java\jdk-26.0.2.1\bin\javac.exe"
if not exist "%JAVAC%" goto :no_compiler

REM Display Java version
echo Checking Java version...
"%JAVAC%" -version

echo.
echo Creating bin directory...
if not exist bin mkdir bin

echo.
echo Compiling source files...
echo.

REM Compile all Java files
"%JAVAC%" --release 8 -d bin -encoding UTF-8 src\*.java

if errorlevel 1 goto :build_failed

echo.
echo ======================================================================
echo BUILD SUCCESSFUL!
echo ======================================================================
echo.
echo Next steps:
echo   1. Run the analyzer:
echo      java -cp bin LogAnalyzer trade_log.txt
echo.
echo   2. In another terminal, run the log generator:
echo      java -cp bin LogGeneratorUtility trade_log.txt
echo.
echo For more information, see README.md
echo.
pause
exit /b 0

:no_compiler
echo ERROR: Java compiler (javac) not found!
echo Please install a Java JDK or add javac to your PATH.
pause
exit /b 1

:build_failed
echo.
echo ======================================================================
echo BUILD FAILED!
echo ======================================================================
echo Please check the errors above
pause
exit /b 1
