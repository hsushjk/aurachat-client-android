@echo off
chcp 65001 >nul
setlocal EnableExtensions
set "ROOT=%~dp0"
set "TOOLS=%USERPROFILE%\tools"
set "JAVA_21="

rem 找 JDK 21
call :probe_java "%TOOLS%\jdk21"
call :probe_java "%JAVA_HOME%"
call :probe_java "%ProgramFiles%\Android\Android Studio\jbr"
if not defined JAVA_21 (
    echo [build] 没找到 JDK 21，先运行 _setup-toolchain.ps1
    exit /b 1
)

rem 找 SDK
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME if exist "%USERPROFILE%\Android\Sdk\platforms" set "ANDROID_HOME=%USERPROFILE%\Android\Sdk"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk\platforms" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME (
    echo [build] 没找到 Android SDK，先运行 _setup-toolchain.ps1
    exit /b 1
)

rem 找 Gradle
set "GRADLE_CMD=%ROOT%gradlew.bat"
if not exist "%GRADLE_CMD%" if exist "%TOOLS%\gradle-8.13\bin\gradle.bat" set "GRADLE_CMD=%TOOLS%\gradle-8.13\bin\gradle.bat"
if not exist "%GRADLE_CMD%" for /f "delims=" %%i in ('where gradle 2^>nul') do set "GRADLE_CMD=%%i"
if not exist "%GRADLE_CMD%" (
    echo [build] 找不到 Gradle，先运行 _setup-toolchain.ps1 或安装 Gradle 8.13
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"
pushd "%ROOT%"
call "%GRADLE_CMD%" assembleDebug %*
set "RC=%ERRORLEVEL%"
popd
if not "%RC%"=="0" (
    echo [build] 编译失败，退出码 %RC%
    exit /b %RC%
)
echo [build] 完成：app\build\outputs\apk\debug\app-debug.apk
exit /b 0

:probe_java
if defined JAVA_21 goto :eof
if not exist "%~1\bin\java.exe" goto :eof
"%~1\bin\java.exe" -XshowSettings:properties -version 2>&1 | findstr /c:"java.specification.version = 21" >nul
if errorlevel 1 goto :eof
set "JAVA_HOME=%~1"
set "JAVA_21=1"
goto :eof
