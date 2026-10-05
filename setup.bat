@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo Arsenic setup (Fabric, Minecraft 26.3)
echo.

rem Minecraft 26.3 and this build need Java 25 or newer. JAVA_HOME often points at an older JDK
rem (e.g. Java 8 for 1.8.9 Forge work), so look for a suitable JDK and use it for this script only;
rem the system-wide JAVA_HOME is left alone.
set "JDK="
if defined JAVA_HOME call :checkjdk "%JAVA_HOME%"
if not defined JDK (
    for %%r in ("%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Java" "%ProgramFiles%\Microsoft"
                "%ProgramFiles%\Zulu" "%ProgramFiles%\Amazon Corretto" "%ProgramFiles%\BellSoft"
                "%LOCALAPPDATA%\Programs\Eclipse Adoptium" "%USERPROFILE%\.jdks") do (
        if exist "%%~r" for /d %%d in ("%%~r\*") do if not defined JDK call :checkjdk "%%~d"
    )
)
if not defined JDK (
    echo No JDK 25 or newer was found. Install one ^(e.g. Eclipse Temurin 25 from adoptium.net^)
    echo and run this again, or point JAVA_HOME at it.
    goto :fail
)
set "JAVA_HOME=%JDK%"
echo Using JDK: %JAVA_HOME%
echo.

rem Downloads Minecraft and the Fabric toolchain, decompiles Minecraft so the IDE can show its
rem sources, and does a first build. Run configs are generated when the IDE imports the project.
rem (two runs: Loom rejects genSources and build in the same invocation)
call "%~dp0gradlew.bat" genSources
if not errorlevel 1 call "%~dp0gradlew.bat" build
if errorlevel 1 (
    echo.
    echo Setup failed. Check the output above.
    goto :fail
)

echo.
echo Done. Open the project folder in your IDE and load the Gradle project.
echo In IntelliJ, set Settings ^> Build Tools ^> Gradle ^> Gradle JVM to this JDK:
echo     %JAVA_HOME%
echo Then use the "Minecraft Client" run configuration. To run Gradle from a terminal instead,
echo point JAVA_HOME at that JDK first.
pause
exit /b 0

:fail
pause
exit /b 1

rem Sets JDK to %1 if it contains a java.exe of version 25 or newer.
:checkjdk
if not exist "%~1\bin\java.exe" exit /b 0
set "VER="
for /f "tokens=3" %%v in ('""%~1\bin\java.exe" -version 2>&1 | findstr /i /c:"version""') do set "VER=%%~v"
if not defined VER exit /b 0
for /f "delims=." %%m in ("!VER!") do set "MAJOR=%%m"
set /a MAJOR=MAJOR 2>nul
if !MAJOR! geq 25 set "JDK=%~1"
exit /b 0
