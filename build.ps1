param(
    [string]$JdkHome=$env:JAVA_HOME,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [string]$KeyStorePath
)
$ErrorActionPreference='Stop'
$source=$PSScriptRoot
$outputDirectory=Join-Path $source 'build'
$build=Join-Path $outputDirectory ('run-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff'))
$signing=Join-Path $source '.signing'
if(!$JdkHome){throw 'Indicá -JdkHome o definí JAVA_HOME con un JDK 17.'}
foreach($file in @((Join-Path $JdkHome 'bin\javac.exe'),$AndroidJar,(Join-Path $BuildTools 'aapt2.exe'))){if(!(Test-Path -LiteralPath $file)){throw "No se encontró $file"}}
New-Item -ItemType Directory -Force "$build\generated","$build\classes","$build\dex",$signing | Out-Null
if(!$KeyStorePath){$KeyStorePath=Join-Path $signing 'hyremote.keystore'}
function Run([string]$exe,[string[]]$arguments){ & $exe @arguments; if($LASTEXITCODE -ne 0){throw "Falló $exe ($LASTEXITCODE)"} }
Run "$BuildTools\aapt2.exe" @('compile','--dir',"$source\res",'-o',"$build\resources.zip")
Run "$BuildTools\aapt2.exe" @('link','-o',"$build\base.apk",'-I',$AndroidJar,'--manifest',"$source\AndroidManifest.xml",'--java',"$build\generated",'-A',"$source\assets",'--min-sdk-version','26','--target-sdk-version','30',"$build\resources.zip")
$javaFiles=@(Get-ChildItem "$source\src","$build\generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
Run "$JdkHome\bin\javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',$AndroidJar,'-d',"$build\classes")+$javaFiles)
Run "$JdkHome\bin\jar.exe" @('cf',"$build\classes.jar",'-C',"$build\classes",'.')
Run "$JdkHome\bin\java.exe" @('-cp',"$BuildTools\lib\d8.jar",'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','26','--output',"$build\dex","$build\classes.jar")
Copy-Item "$build\base.apk" "$build\unsigned.apk" -Force
Run "$JdkHome\bin\jar.exe" @('uf',"$build\unsigned.apk",'-C',"$build\dex",'classes.dex')
Run "$BuildTools\zipalign.exe" @('-f','4',"$build\unsigned.apk","$build\aligned.apk")
$temporaryPassword=$false
try{
    if(!$env:HY_REMOTE_KEYSTORE_PASSWORD){
        $secure=Read-Host 'Contraseña de tu firma local' -AsSecureString
        $env:HY_REMOTE_KEYSTORE_PASSWORD=[System.Net.NetworkCredential]::new('',$secure).Password
        $temporaryPassword=$true
    }
    if(!(Test-Path -LiteralPath $KeyStorePath)){
        Run "$JdkHome\bin\keytool.exe" @('-genkeypair','-keystore',$KeyStorePath,'-storepass:env','HY_REMOTE_KEYSTORE_PASSWORD','-keypass:env','HY_REMOTE_KEYSTORE_PASSWORD','-alias','hyremote','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=HY Remote Local Build')
    }
    Run "$JdkHome\bin\java.exe" @('-jar',"$BuildTools\lib\apksigner.jar",'sign','--ks',$KeyStorePath,'--ks-pass','env:HY_REMOTE_KEYSTORE_PASSWORD','--out',"$outputDirectory\HY-Remote.apk","$build\aligned.apk")
    Run "$JdkHome\bin\java.exe" @('-jar',"$BuildTools\lib\apksigner.jar",'verify',"$outputDirectory\HY-Remote.apk")
}finally{if($temporaryPassword){Remove-Item Env:HY_REMOTE_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue}}
Get-FileHash "$outputDirectory\HY-Remote.apk" -Algorithm SHA256
