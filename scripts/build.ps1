# Build script for GymLog (portable toolchain)
param(
    [string]$Task = ":app:assembleDebug",
    [switch]$Release
)

$root = "C:\Users\an1on\Desktop\4epkaLll_train"
$env:JAVA_HOME = "$root\.tools\jdk\jdk-17.0.2"
$env:GRADLE_USER_HOME = "$root\.gradle-home"
$env:ANDROID_USER_HOME = "$root\.android-home"
Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
Remove-Item Env:ANDROID_AVD_HOME -ErrorAction SilentlyContinue
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if ($Release) { $Task = ":app:assembleRelease" }

& "$root\.tools\gradle\gradle-8.10.2\bin\gradle.bat" -p $root $Task --console=plain @args
exit $LASTEXITCODE