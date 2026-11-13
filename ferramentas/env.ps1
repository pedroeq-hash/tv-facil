# Ativa JDK, Android SDK e Gradle portateis SO nesta janela do PowerShell (nada no registro/variaveis globais).
# Uso: . .\ferramentas\env.ps1      (com o ponto e espaco na frente)
$base = 'C:\dev\android-tools'
$env:JAVA_HOME = "$base\jdk-17"
$env:ANDROID_HOME = "$base\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$gradle = (Get-ChildItem "$base\gradle-*" -Directory | Sort-Object Name | Select-Object -Last 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$gradle\bin;C:\scrcpy;$env:PATH"
Write-Host "Ferramentas ativas: JDK=$env:JAVA_HOME | SDK=$env:ANDROID_HOME | Gradle=$gradle"
