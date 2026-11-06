param(
 [string]$JavaHome = $env:JAVA_HOME,
 [string]$Sdk = $env:ANDROID_HOME,
 [string]$Gradle = '',
 [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$workspace = Split-Path $project -Parent
if (!(Test-Path -LiteralPath "$JavaHome\bin\java.exe")) { throw 'Specify -JavaHome (JDK 17+)' }
if (!(Test-Path -LiteralPath "$Sdk\platforms\android-35\android.jar")) { throw 'Specify -Sdk with Android API 35 installed' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $Sdk
$env:GRADLE_USER_HOME = Join-Path $workspace 'tmp/gradle-home'
# Reuse installed artifacts read-only, without writing the user cache.
if (Test-Path -LiteralPath "$env:USERPROFILE\.gradle\caches\modules-2") { $env:GRADLE_RO_DEP_CACHE = "$env:USERPROFILE\.gradle\caches" }
New-Item -ItemType Directory -Path (Join-Path $workspace 'tmp') -Force | Out-Null
$keystore = Join-Path $workspace 'tmp/android-debug.keystore'
if (!(Test-Path -LiteralPath $keystore)) {
 # Standard Android debug identity only; never used for release signing.
 & "$JavaHome\bin\keytool.exe" -genkeypair -keystore $keystore -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
 if ($LASTEXITCODE) { throw 'Debug signing identity generation failed' }
}
if (!$Gradle) { $Gradle = Join-Path $project 'gradlew.bat' }
$buildArgs = @('-p', $project, 'assembleDebug', 'lintDebug', '--no-daemon', '--console=plain')
if ($Offline) { $buildArgs += '--offline' }
& $Gradle @buildArgs
if ($LASTEXITCODE) { throw 'Android build or Lint failed' }
$apk = Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'Build returned without an APK' }
[PSCustomObject]@{
 Path = $apk
 Bytes = (Get-Item -LiteralPath $apk).Length
 SHA256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
}
