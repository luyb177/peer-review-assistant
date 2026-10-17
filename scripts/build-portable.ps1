param(
    [string]$RuntimeDir,
    [string]$DemoConfig,
    [switch]$SkipBuild,
    [switch]$WithoutDemoConfig
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $SkipBuild) {
    Push-Location $root
    try {
        $portableMaven = Join-Path $root '.tools\maven\apache-maven-3.9.9\bin\mvn.cmd'
        if (Test-Path $portableMaven) { & $portableMaven clean package } else { & mvn clean package }
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    } finally { Pop-Location }
}
$jar = Join-Path $root 'target\peer-review-assistant.jar'
if (-not (Test-Path $jar)) { throw 'Build the runnable JAR first.' }
if (-not $RuntimeDir) {
    $candidates = Get-ChildItem (Join-Path $root '.tools\jre17') -Directory -ErrorAction SilentlyContinue
    $RuntimeDir = ($candidates | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1).FullName
}
if (-not $RuntimeDir -or -not (Test-Path (Join-Path $RuntimeDir 'bin\java.exe'))) { throw 'Pass -RuntimeDir with an official Windows x64 Java 17 JRE.' }
if ($WithoutDemoConfig -and $DemoConfig) { throw 'Choose either -WithoutDemoConfig or -DemoConfig.' }
$settings = @{}
if (-not $WithoutDemoConfig) {
if (-not $DemoConfig) { $DemoConfig = Join-Path $root 'config\release-demo.local.properties' }
if (-not (Test-Path $DemoConfig)) { throw 'A private dedicated course-demo configuration is required; never use development/root credentials.' }
Get-Content -LiteralPath $DemoConfig -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1].Trim()] = $matches[2] }
}
foreach ($key in @('db.url','db.user','db.password','ai.baseUrl','ai.apiKey','ai.model','demo.teacher.username','demo.teacher.password')) {
    if (-not $settings[$key] -or $settings[$key] -match 'YOUR_|PLACEHOLDER|^SET_') { throw "Release setting missing: $key (value not printed)." }
}
if ($settings['db.user'] -eq 'root') { throw 'Never distribute a database root account.' }
if ($settings['ai.baseUrl'] -match 'localhost|127\.0\.0\.1|\[::1\]') { throw 'Default AI must be reachable from another computer, not a local gateway.' }
}
$version = ([xml](Get-Content (Join-Path $root 'pom.xml') -Raw)).project.version
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stagingParent = Join-Path $root "target\portable-$stamp"
$staging = Join-Path $stagingParent 'peer-review-assistant'
New-Item -ItemType Directory -Path $staging -Force | Out-Null
foreach ($folder in @('bin','config','docs','sql')) { New-Item -ItemType Directory -Path (Join-Path $staging $folder) | Out-Null }
Copy-Item $jar (Join-Path $staging 'bin\peer-review-assistant.jar')
Copy-Item (Join-Path $root 'run.cmd') $staging
Copy-Item $RuntimeDir (Join-Path $staging 'runtime') -Recurse
if (-not $WithoutDemoConfig) { Copy-Item $DemoConfig (Join-Path $staging 'config\course-demo.properties') }
Copy-Item (Join-Path $root 'config\application.example.properties') (Join-Path $staging 'config')
if ($settings['db.trustStore']) {
    $store = $settings['db.trustStore']
    if ([IO.Path]::IsPathRooted($store) -or $store -match '[\\/]') { throw 'Demo trust store must use a leaf filename relative to the config directory.' }
    Copy-Item (Join-Path (Split-Path $DemoConfig -Parent) $store) (Join-Path (Join-Path $staging 'config') $store)
}
Copy-Item (Join-Path $root 'src\main\resources\schema.sql') (Join-Path $staging 'sql')
Copy-Item (Join-Path $root 'sql\01-review-version.sql') (Join-Path $staging 'sql')
Copy-Item (Join-Path $root 'sql\02-document-attachments.sql') (Join-Path $staging 'sql')
if (Test-Path (Join-Path $root 'docs\运行与配置.md')) { Copy-Item (Join-Path $root 'docs\运行与配置.md') (Join-Path $staging 'docs') }
foreach ($guide in @('用户简易使用手册.md','用户简易使用手册.docx')) {
    Copy-Item (Join-Path $root "docs\$guide") (Join-Path $staging 'docs')
}
$quickstart = @"
课堂同伴互评助手 - Windows x64 免安装运行包

1. 解压整个目录后双击 run.cmd。不需要 IDEA、Maven 或系统 Java。
2. 默认服务仅供课程验收，结课后会关闭。演示数据公开共享，请勿提交真实个人信息。
3. 演示教师：$($settings['demo.teacher.username'])
   演示密码：$($settings['demo.teacher.password'])
   演示学生 student01 至 student06，密码 Demo12345!
4. 在“连接配置”分别切换 MySQL 和 AI，可以保存多个方案。
5. MySQL 会检查结构并创建缺表/专用空库，已知旧版本安全补字段；结构冲突不会清空重建。
6. 自备数据库账号需相应 CREATE/REFERENCES 权限；旧版本升级需 ALTER。切库后重新登录，新空库请初始化教师账号。
7. AI 连通性测试仅查询模型列表，不发送作业、不生成评语。
8. 配置明文保存在本机 config 下。默认演示凭据是可撤销的服务凭据，不是保密容器。
"@
if ($WithoutDemoConfig) {
    $quickstart = @"
课堂同伴互评助手 $version - Windows x64 免安装运行包

1. 解压整个目录后双击 run.cmd，已附 Java 17，无需 IDEA、Maven 或系统 Java。
2. 此包不含数据库密码和 AI Key。在登录页“连接配置”填写自己的 MySQL 及 AI 服务。
3. 新库使用“初始化专用库”创建教师账号，可选择加入合成演示数据。
4. AI 未配置时可正常开展人工互评；使用 AI 参考意见须另行配置可用接口。
5. 操作流程见 docs/用户简易使用手册.docx。sql 目录提供完整建表与旧版迁移脚本。
"@
}
[IO.File]::WriteAllText((Join-Path $staging 'README-运行.txt'), $quickstart, [Text.UTF8Encoding]::new($false))
$manifest = @{ version=$version; platform='windows-x64'; runtime='bundled Java 17'; includesDemoConfig=(-not $WithoutDemoConfig.IsPresent); created=(Get-Date -Format o); jarSha256=(Get-FileHash $jar -Algorithm SHA256).Hash }
$manifest | ConvertTo-Json | Set-Content (Join-Path $staging 'release-manifest.json') -Encoding UTF8
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $root "target\peer-review-assistant-$version-windows-x64-$stamp.zip"
[IO.Compression.ZipFile]::CreateFromDirectory($stagingParent, $zip, [IO.Compression.CompressionLevel]::Optimal, $false)
(Get-FileHash $zip -Algorithm SHA256).Hash | Set-Content "$zip.sha256" -Encoding ASCII
Write-Output "Portable release created: $zip"
Write-Output "Staging folder: $staging"
Write-Output "Includes course-demo settings: $(-not $WithoutDemoConfig.IsPresent). Personal development configuration was not copied."
