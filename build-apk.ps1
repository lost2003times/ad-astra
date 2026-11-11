# Builds the Ad Astra APK using the tools installed in L:\Android.
# Output: app\build\outputs\apk\debug\app-debug.apk
$env:JAVA_HOME = "L:\Android\jdk17"
$env:ANDROID_HOME = "L:\Android\sdk"
$env:GRADLE_USER_HOME = "L:\Android\gradle-home"
$env:ANDROID_USER_HOME = "L:\Android\android-user-home"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
Set-Location $PSScriptRoot
.\gradlew.bat assembleDebug
