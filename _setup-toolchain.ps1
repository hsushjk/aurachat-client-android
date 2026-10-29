param(
    [string]$ToolsDir = (Join-Path $env:USERPROFILE 'tools'),
    [string]$SdkDir = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

if (-not $SdkDir) {
    if ($env:ANDROID_HOME) { $SdkDir = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $SdkDir = $env:ANDROID_SDK_ROOT }
    else { $SdkDir = Join-Path $env:USERPROFILE 'Android\Sdk' }
}

New-Item -ItemType Directory -Path $ToolsDir -Force | Out-Null
New-Item -ItemType Directory -Path $SdkDir -Force | Out-Null

$jdkDir = Join-Path $ToolsDir 'jdk21'
if (-not (Test-Path (Join-Path $jdkDir 'bin\java.exe'))) {
    Write-Host '下载 JDK 21 ...'
    $zip = Join-Path $ToolsDir 'jdk21.zip'
    $url = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
    Invoke-WebRequest $url -OutFile $zip -MaximumRedirection 5
    Expand-Archive $zip -DestinationPath $ToolsDir -Force
    $unpacked = Get-ChildItem $ToolsDir -Directory -Filter 'jdk-21*' | Select-Object -First 1
    if (-not $unpacked) { throw '解压后找不到jdk-21*' }
    Move-Item $unpacked.FullName $jdkDir -Force
    Remove-Item $zip -Force
}
$javaExe = Join-Path $jdkDir 'bin\java.exe'
$javaVer = (& cmd /c "`"$javaExe`" -version 2>&1" | Select-Object -First 1)
if ($javaVer -notmatch '"21\.') { throw "JDK 版本不对，需要 21：$javaVer" }
Write-Host "JDK 21：$jdkDir"
Write-Host "        $javaVer"

# Android command line tools
$sdkManager = Join-Path $SdkDir 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
    Write-Host '下载 Android command line tools ...'
    $zip = Join-Path $ToolsDir 'cmdline-tools.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $zip
    $tmp = Join-Path $ToolsDir 'cmdline-tools-tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive $zip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Path (Join-Path $SdkDir 'cmdline-tools') -Force | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $SdkDir 'cmdline-tools\latest') -Force
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
}

$env:JAVA_HOME = $jdkDir
$env:ANDROID_HOME = $SdkDir

Write-Host '接受 SDK 许可协议 ...'
$ErrorActionPreference = 'Continue'
@('y') * 50 | & $sdkManager --sdk_root=$SdkDir --licenses 2>&1 | Out-Null
$licCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($licCode -ne 0) { throw "接受 SDK 许可失败（sdkmanager 退出码 $licCode）" }

Write-Host '安装 platform-tools / android-35 / build-tools 35 ...'
$ErrorActionPreference = 'Continue'
& $sdkManager --sdk_root=$SdkDir 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 2>&1 | Out-Null
$instCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($instCode -ne 0) { throw "安装 Android SDK 组件失败（sdkmanager 退出码 $instCode）" }

Write-Host ''
Write-Host "Android SDK：$SdkDir"
