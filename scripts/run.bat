@echo off
REM Launch the Review Board Scheduler (rebuilt jar) on Windows.
REM Replaces the legacy RunScheduler.bat, minus the hardcoded API key:
REM the SignUpGenius key is read from the SUG_KEY environment variable,
REM or from an untracked .env file in the repo root (copy .env.example).
setlocal enabledelayedexpansion
cd /d "%~dp0.."

if "%SUG_KEY%"=="" if exist .env (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        if "%%a"=="SUG_KEY" set "SUG_KEY=%%b"
    )
)

set "JAR="
for %%j in (target\eagleboardscheduler-*.jar) do set "JAR=%%j"
if "%JAR%"=="" (
    echo No built jar found — building with mvnw.cmd package ...
    call mvnw.cmd -q package || exit /b 1
    for %%j in (target\eagleboardscheduler-*.jar) do set "JAR=%%j"
)

set "SUGARG="
if not "%SUG_KEY%"=="" if not "%SUG_KEY%"=="replace-with-real-key" set SUGARG=-sugkey %SUG_KEY%

REM Serve only the venue wifi (192.168.x). Without -bind the app listens on
REM every interface and pops one dialog per adapter, including Hyper-V/WSL.
REM Override for a different network, e.g. set EB_BIND=10.0.
if "%EB_BIND%"=="" set "EB_BIND=192.168."

java -jar "%JAR%" -verbose -w -a Master_AdultHistory.csv -c config.properties -port 8080 -bind %EB_BIND% %SUGARG%
pause
