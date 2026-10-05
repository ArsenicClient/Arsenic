@echo off
setlocal
cd /d "%~dp0"

echo Arsenic setup (Fabric, Minecraft 26.3)
echo.

rem Minecraft 26.3 and this build need Java 25 or newer.
where java >nul 2>nul
if errorlevel 1 if not defined JAVA_HOME (
    echo Java was not found. Install JDK 25 ^(e.g. Eclipse Temurin 25^) and set JAVA_HOME.
    goto :fail
)

rem Downloads Minecraft and the Fabric toolchain, decompiles Minecraft so the IDE can show its
rem sources, and does a first build. Run configs are generated when the IDE imports the project.
rem (two runs: Loom rejects genSources and build in the same invocation)
call "%~dp0gradlew.bat" genSources
if not errorlevel 1 call "%~dp0gradlew.bat" build
if errorlevel 1 (
    echo.
    echo Setup failed. Check the output above; it is usually a JDK older than 25.
    goto :fail
)

echo.
echo Done. Open the project folder in your IDE and load the Gradle project.
echo Use the "Minecraft Client" run configuration, or run: gradlew runClient
pause
exit /b 0

:fail
pause
exit /b 1
