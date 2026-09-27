@echo off
REM Launch the Eagle Board Scheduler (rebuilt jar) on Windows.
REM Replaces the legacy RunScheduler.bat, minus the hardcoded API key:
REM the SignUpGenius key is read from the SUG_KEY environment variable,
REM or from an untracked .env file in the repo root (copy .env.example).
REM
REM Keep this in step with scripts/run.sh. Windows is the machine this
REM actually runs on at an event, so any guard added there belongs here too.
setlocal enabledelayedexpansion
cd /d "%~dp0.."

if "%SUG_KEY%"=="" if exist .env (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        if "%%a"=="SUG_KEY" set "SUG_KEY=%%b"
    )
)

call :pickjar
if not defined JAR (
    echo No built jar found - building with mvnw.cmd package ...
    call mvnw.cmd -q package || exit /b 1
    call :pickjar
)
if not defined JAR (
    echo ERROR: no jar in target\ even after building. Check the build output.
    pause
    exit /b 1
)

REM Say which build is starting, and name any older ones still sitting there.
REM Picking the newest is a good guess, not a guarantee -- the only way to be
REM certain target\ holds exactly what the source says is a clean build.
if !JARCOUNT! GTR 1 (
    echo NOTE: target\ holds more than one build. Starting the newest:
    echo         !JAR!
    echo       Older jars are still there. "mvnw.cmd clean package" clears them:
    for /f "delims=" %%j in ('dir /b /a-d /o-d "target\eagleboardscheduler-*.jar" 2^>nul') do (
        if /i not "target\%%j"=="!JAR!" echo         target\%%j
    )
)

REM WebServer.sendResponseFile checks the FILESYSTEM before the classpath: it
REM tries `new File("WEBROOT", name)` first and only falls back to the copy
REM packaged in the jar when that does not exist. The path is relative, so it
REM resolves against this working directory -- the repo root, thanks to the cd
REM above. A stray WEBROOT\ here therefore shadows the entire UI, and every
REM rebuild is both real and completely ignored. Original inherited behaviour,
REM which let operators patch a page without rebuilding, so this warns rather
REM than refusing, and does not touch the folder.
if exist "WEBROOT\" (
    echo WARNING: a WEBROOT\ directory exists in "%CD%".
    echo          The server serves pages from there IN PREFERENCE to the ones
    echo          built into the jar, so what you see will be whatever that
    echo          folder holds no matter how many times you rebuild.
    echo          Move it aside if you did not put it there deliberately.
)

set "SUGARG="
if not "%SUG_KEY%"=="" if not "%SUG_KEY%"=="replace-with-real-key" set SUGARG=-sugkey %SUG_KEY%

REM Serve only the venue wifi (192.168.x). Without -bind the app listens on
REM every interface and pops one dialog per adapter, including Hyper-V/WSL.
REM Override for a different network, e.g. set EB_BIND=10.0.
if "%EB_BIND%"=="" set "EB_BIND=192.168."

REM Quiet by default, as in run.sh: -verbose logs every request, and with it
REM the SignUpGenius import prints each registrant and the API key. Set
REM EB_VERBOSE=1 to turn it on for chasing a problem.
set "VFLAG="
if not "%EB_VERBOSE%"=="" (
    set "VFLAG=-verbose"
    echo EB_VERBOSE is set: logging every request, and the API key.
)

REM EB_DRYRUN=1 stops here without launching anything. It exists so the
REM Windows CI leg can prove this script picks the newest jar, prints the
REM shadowed-WEBROOT warning, and starts quiet. None of that is checkable
REM from Linux or macOS, and this launcher has drifted behind run.sh once
REM already.
if not "%EB_DRYRUN%"=="" (
    echo DRYRUN: would start !JAR!
    echo DRYRUN: options [!VFLAG!] -w -a Master_AdultHistory.csv -c config.properties -port 8080 -bind %EB_BIND%
    exit /b 0
)

java -jar "!JAR!" !VFLAG! -w -a Master_AdultHistory.csv -c config.properties -port 8080 -bind %EB_BIND% %SUGARG%
pause
exit /b

REM Pick the NEWEST jar in target\, and count how many are there.
REM `dir /o-d` sorts by timestamp, newest first. A plain wildcard glob would
REM lean on alphabetical order happening to match chronological order, which
REM only holds while the version stays a sortable date. `mvnw.cmd package`
REM never removes the previous version's jar, so they accumulate in target\
REM and an older build starts up looking by every outward sign like the
REM current one: same window, same port, same pages.
REM The pattern is anchored at the start of the filename, so the shade
REM plugin's original-eagleboardscheduler-*.jar is not a match.
:pickjar
set "JAR="
set "JARCOUNT=0"
for /f "delims=" %%j in ('dir /b /a-d /o-d "target\eagleboardscheduler-*.jar" 2^>nul') do (
    set /a JARCOUNT+=1
    if not defined JAR set "JAR=target\%%j"
)
goto :eof
