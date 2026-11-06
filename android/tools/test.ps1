param(
 [string]$JavaHome = $env:JAVA_HOME,
 [string]$Sdk = $env:ANDROID_HOME,
 [string]$JsonJar = $env:NJU_JSON_JAR
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath "$JavaHome\bin\javac.exe")) { throw 'Specify -JavaHome (JDK 17+)' }
if (!(Test-Path -LiteralPath "$Sdk\platforms\android-35\android.jar")) { throw 'Specify -Sdk with Android API 35 installed' }
if (!(Test-Path -LiteralPath $JsonJar)) { throw 'Specify -JsonJar containing org.json (host tests only)' }
$output = Join-Path $project '../tmp/host-tests'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$sources = Join-Path $project 'app/src/main/java/edu/nju/homework'
$classpath = "$JsonJar;$Sdk\platforms\android-35\android.jar"
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp $classpath -d $output "$sources\Store.java" "$sources\Csv.java" "$sources\NavigationPolicy.java" "$sources\GradeSummary.java" "$sources\RefreshPolicy.java" "$sources\GoogleCalendarSync.java" "$project\tests\StoreChecks.java" "$project\tests\NavigationChecks.java" "$project\tests\GradeSummaryChecks.java" "$project\tests\RefreshPolicyChecks.java" "$project\tests\GoogleCalendarChecks.java"
if ($LASTEXITCODE) { throw 'Host test compilation failed' }
& "$JavaHome\bin\java.exe" -cp "$output;$classpath" edu.nju.homework.StoreChecks
if ($LASTEXITCODE) { throw 'Store/CSV checks failed' }
& "$JavaHome\bin\java.exe" -cp "$output;$classpath" edu.nju.homework.NavigationChecks
if ($LASTEXITCODE) { throw 'Navigation checks failed' }
& "$JavaHome\bin\java.exe" -cp "$output;$classpath" edu.nju.homework.GradeSummaryChecks
if ($LASTEXITCODE) { throw 'Grade summary checks failed' }
& "$JavaHome\bin\java.exe" -cp "$output;$classpath" edu.nju.homework.RefreshPolicyChecks
if ($LASTEXITCODE) { throw 'Refresh policy checks failed' }
& "$JavaHome\bin\java.exe" -cp "$output;$classpath" edu.nju.homework.GoogleCalendarChecks
if ($LASTEXITCODE) { throw 'Google Calendar checks failed' }
node --test "$project/tests/readers.test.js" "$project/tests/grades.test.js" "$project/tests/entry.test.js"
if ($LASTEXITCODE) { throw 'Reader checks failed' }
