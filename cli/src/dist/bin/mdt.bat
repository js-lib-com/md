@echo off
rem Markdown tools launcher: mdt <command> [options] [document-dir]
rem Run 'mdt --help' for usage. Requires Java 17 or newer, from JAVA_HOME or PATH.
rem Extra JVM options can be set with MDT_JAVA_OPTS environment variable.
rem No parenthesized blocks are used since paths like 'C:\Program Files (x86)' would break them.
setlocal

set "MDT_HOME=%~dp0.."

set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if defined JAVA_HOME if not exist "%JAVA_EXE%" goto :noJavaHome
if defined JAVA_HOME goto :run
where java.exe >nul 2>nul || goto :noJava

:run
"%JAVA_EXE%" %MDT_JAVA_OPTS% -cp "%MDT_HOME%\conf;%MDT_HOME%\lib\*" com.jslib.md.cli.Main %*
exit /b %ERRORLEVEL%

:noJavaHome
echo mdt: Java not found at "%JAVA_EXE%"; please check JAVA_HOME. 1>&2
exit /b 1

:noJava
echo mdt: Java not found; please install Java 17 or newer, or set JAVA_HOME. 1>&2
exit /b 1
