<#
使用本机 IDEA SDK 和原版插件编译修复类，保留其余已发布的二进制与界面资源。
生成安装 ZIP，不修改正在使用的插件目录。需要 PowerShell 7 和带 javac 的 IDEA JBR。
#>
param(
    [Parameter(Mandatory)][string]$IdeaHome,
    [Parameter(Mandatory)][string]$PluginHome
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$module = Join-Path $PSScriptRoot 'mybatis-idea-plugin'
$build = Join-Path $PSScriptRoot ('build/compat-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$staging = Join-Path $build 'MybatisLog'
$descriptor = Join-Path $module 'src/main/resources/META-INF/plugin.xml'
$version = ([xml](Get-Content -LiteralPath $descriptor -Raw)).'idea-plugin'.version
$originalJars = @(Get-ChildItem -LiteralPath (Join-Path $PluginHome 'lib') -Filter '*mybatis-idea-plugin-*.jar')
if ($originalJars.Count -ne 1) { throw '必须提供原版 MyBatis Log EasyPlus 的安装目录。' }
$javac = Join-Path $IdeaHome 'jbr/bin/javac.exe'
$java = Join-Path $IdeaHome 'jbr/bin/java.exe'
if (!(Test-Path -LiteralPath $javac)) { throw 'IDEA JBR 中未找到 javac。' }
New-Item -ItemType Directory -Path $classes, $staging -Force | Out-Null
$classpath = "$IdeaHome/lib/*;$IdeaHome/plugins/java/lib/*;$PluginHome/lib/*"
$source = Join-Path $module 'src/main/java/com/plugins/mybaitslog/console/PrintlnUtil.java'
$test = Join-Path $module 'src/test/java/com/plugins/mybaitslog/console/PrintlnUtilCompatibilityTest.java'
$formatter = Join-Path $module 'src/main/java/com/plugins/mybaitslog/console/BasicFormatter.java'
$formatterTest = Join-Path $module 'src/test/java/com/plugins/mybaitslog/console/BasicFormatterTest.java'
& $javac --release 8 -Xlint:-options -encoding UTF-8 -proc:none -cp $classpath -d $classes $source $test $formatter $formatterTest
if ($LASTEXITCODE -ne 0) { throw '修复类编译失败。' }
& $java -cp "$classes;$classpath" com.plugins.mybaitslog.console.PrintlnUtilCompatibilityTest
if ($LASTEXITCODE -ne 0) { throw 'SQL 类型回归验证失败。' }
& $java -cp "$classes;$classpath" com.plugins.mybaitslog.console.BasicFormatterTest
if ($LASTEXITCODE -ne 0) { throw 'SQL 排版回归验证失败。' }

# 仅替换修复类和版本号，保留原版经 IDEA 处理的窗体等资源。
Copy-Item -LiteralPath (Join-Path $PluginHome 'lib') -Destination $staging -Recurse
$jarPath = Join-Path $staging ('lib/' + $originalJars[0].Name)
$zip = [System.IO.Compression.ZipFile]::Open($jarPath, 'Update')
try {
    foreach ($className in @('PrintlnUtil.class', 'BasicFormatter.class', 'BasicFormatter$FormatProcess.class')) {
        $entryName = 'com/plugins/mybaitslog/console/' + $className
        $zip.GetEntry($entryName).Delete()
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $classes $entryName), $entryName) | Out-Null
    }
    $zip.GetEntry('META-INF/plugin.xml').Delete()
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $descriptor, 'META-INF/plugin.xml') | Out-Null
} finally { $zip.Dispose() }

# 验证最终 JAR 中的代码，类路径优先加载打包后的修复类。
$packedClasspath = "$jarPath;$classes;$IdeaHome/lib/*;$IdeaHome/plugins/java/lib/*;$staging/lib/*"
& $java -cp $packedClasspath com.plugins.mybaitslog.console.PrintlnUtilCompatibilityTest
if ($LASTEXITCODE -ne 0) { throw '最终安装包回归验证失败。' }
& $java -cp $packedClasspath com.plugins.mybaitslog.console.BasicFormatterTest
if ($LASTEXITCODE -ne 0) { throw '最终安装包 SQL 排版验证失败。' }
$output = Join-Path $build "MybatisLog-$version.zip"
Compress-Archive -LiteralPath $staging -DestinationPath $output
Write-Output "安装包: $output"
Get-FileHash -LiteralPath $output -Algorithm SHA256
