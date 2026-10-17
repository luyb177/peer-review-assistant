param(
    [Parameter(Mandatory=$true)][string]$Bundle,
    [ValidateSet('--all-features','--resume-all-features','--review-mutations','--post-check','--configuration-only','--resume-supplement')][string]$Mode='--all-features',
    [string]$Resume,
    [switch]$AllowAiOnce
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$bundlePath=(Resolve-Path -LiteralPath $Bundle).Path
$java=Join-Path $bundlePath 'runtime/bin/java.exe'
$product=Join-Path $bundlePath 'bin/peer-review-assistant.jar'
$config=Join-Path $bundlePath 'config/application.properties'
foreach($path in @($java,$product,$config)){if(-not (Test-Path -LiteralPath $path)){throw "Required file missing: $path"}}
if($Mode -in @('--all-features','--resume-all-features') -and -not $AllowAiOnce){throw 'This scenario performs a real AI generation. Pass -AllowAiOnce after explicit user authorization.'}
if($Mode -in @('--resume-all-features','--post-check','--configuration-only','--resume-supplement') -and -not $Resume){throw 'Pass the existing private batch manifest with -Resume.'}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$agent=Join-Path $root "target/packaged-live-driver-$stamp.jar"
$options=Join-Path $root "target/packaged-live-options-$stamp.properties"
$log=Join-Path $root "target/packaged-live-$stamp.log"
Add-Type -AssemblyName System.IO.Compression
$zip=[IO.Compression.ZipArchive]::new([IO.File]::Create($agent),[IO.Compression.ZipArchiveMode]::Create)
try {
    $entry=$zip.CreateEntry('META-INF/MANIFEST.MF')
    $writer=[IO.StreamWriter]::new($entry.Open(),[Text.UTF8Encoding]::new($false))
    $writer.Write("Manifest-Version: 1.0`nPremain-Class: edu.course.peerreview.ui.PackagedLiveAgent`n`n");$writer.Dispose()
    $classes=Join-Path $root 'target/test-classes/edu/course/peerreview/ui'
    $drivers=@(Get-ChildItem -LiteralPath $classes -Filter 'LiveAcceptance*.class')+@(Get-Item (Join-Path $classes 'PackagedLiveAgent.class'))
    foreach($driver in $drivers){
        $entry=$zip.CreateEntry('edu/course/peerreview/ui/'+$driver.Name)
        $sink=$entry.Open();$inputStream=[IO.File]::OpenRead($driver.FullName)
        try{$inputStream.CopyTo($sink)}finally{$inputStream.Dispose();$sink.Dispose()}
    }
}finally{$zip.Dispose()}
$lines=@("root=$($root.Replace('\','/'))","jar=$($product.Replace('\','/'))","mode=$Mode")
if($Resume){$lines+="resume=$((Resolve-Path -LiteralPath $Resume).Path.Replace('\','/'))"}
[IO.File]::WriteAllLines($options,$lines,[Text.UTF8Encoding]::new($false))
$oldConfig=$env:PEER_REVIEW_ACCEPTANCE_CONFIG
$env:PEER_REVIEW_ACCEPTANCE_CONFIG=$config
$workdir=Join-Path $root "target/unrelated-live-$stamp"
New-Item -ItemType Directory -Path $workdir | Out-Null
Write-Output "Actual final JAR with bundled runtime; test mode: $Mode"
Write-Output "Test log: $log"
Push-Location $workdir
try {
    & $java '-Dfile.encoding=UTF-8' "-javaagent:$agent=$options" -jar $product *> $log
    $result=$LASTEXITCODE
}finally{Pop-Location;$env:PEER_REVIEW_ACCEPTANCE_CONFIG=$oldConfig}
Get-Content -LiteralPath $log -Tail 30
exit $result
