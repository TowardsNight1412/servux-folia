<#
.SYNOPSIS
    Builds the Servux Folia/Paper plugin jar.

.DESCRIPTION
    Compiles src/main/java against the Folia 26.3 server jar + its libraries, copies
    src/main/resources into the jar and writes dist/servux-paper-26.3-0.12.2.jar.

    Dependencies are NOT vendored. Put every jar from the Folia server's
    `libraries/` folder, plus `versions/26.3/folia-26.3.jar`, into `libs/`.

.PARAMETER Libs
    Directory containing the compile classpath jars. Defaults to <repo>/libs.

.PARAMETER Out
    Output jar path. Defaults to <repo>/dist/servux-paper-26.3-0.12.2.jar.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/build-plugin.ps1
#>
param(
    [string]$Libs = '',
    [string]$Out = ''
)

$ErrorActionPreference = 'Stop'

$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if ([string]::IsNullOrWhiteSpace($Libs)) { $Libs = Join-Path $project 'libs' }
if ([string]::IsNullOrWhiteSpace($Out))  { $Out  = Join-Path $project 'dist\servux-paper-26.3-0.12.2.jar' }

$src = Join-Path $project 'src\main\java'
$res = Join-Path $project 'src\main\resources'
$build = Join-Path $project 'build'
$classes = Join-Path $build 'classes'

# ---- locate JDK (26 recommended, 21+ works) ----
function Find-JdkTool([string]$tool)
{
    $fileName = "$tool.exe"
    $candidates = @()

    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\$fileName") }

    foreach ($root in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium',
                        'C:\Program Files\Zulu', 'C:\Program Files\Microsoft'))
    {
        if (-not (Test-Path $root)) { continue }

        foreach ($pattern in @(
            (Join-Path $root "bin\$fileName"),
            (Join-Path $root "*\bin\$fileName"),
            (Join-Path $root "*\*\bin\$fileName")))
        {
            $candidates += Get-ChildItem $pattern -File -ErrorAction SilentlyContinue |
                           ForEach-Object { $_.FullName }
        }
    }

    $cmd = Get-Command $tool -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }

    # Prefer the newest JDK: Folia 26.3's class files require a recent javac.
    foreach ($candidate in ($candidates | Sort-Object -Unique -Descending))
    {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }

    return $null
}

$javac = Find-JdkTool 'javac'
$jar   = Find-JdkTool 'jar'

if (-not $javac -or -not $jar) {
    throw 'JDK tools not found. Install a JDK 21+ (JDK 26 recommended) and set JAVA_HOME.'
}

# ---- check dependencies ----
$jars = @(Get-ChildItem $Libs -Filter *.jar -File -ErrorAction SilentlyContinue)

if ($jars.Count -lt 10) {
    Write-Host ''
    Write-Host "Missing dependencies in: $Libs" -ForegroundColor Yellow
    Write-Host 'Copy the following into libs/ (do not commit them):'
    Write-Host '  1) every .jar from <Folia server>/libraries/ (recursively)'
    Write-Host '  2) <Folia server>/versions/26.3/folia-26.3.jar'
    Write-Host ''
    exit 1
}

Write-Host "Using $($jars.Count) dependency jars from $Libs"

# ---- compile ----
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes, (Split-Path $Out -Parent) | Out-Null

$sources = Get-ChildItem $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "Compiling $($sources.Count) source files..."

$argFile = Join-Path $build 'javac-sources.args'
$lines = $sources | ForEach-Object { "`"$($_ -replace '\\','/')`"" }
[System.IO.File]::WriteAllLines($argFile, $lines, (New-Object System.Text.UTF8Encoding($false)))

$env:JAVA_TOOL_OPTIONS = '-Duser.language=en -Duser.country=US'
& $javac -encoding UTF-8 -nowarn -g -Xmaxerrs 500 -source 21 -target 21 `
    -cp "$Libs\*" -d $classes "@$argFile"

if ($LASTEXITCODE -ne 0) {
    Write-Host "BUILD FAILED (javac exit $LASTEXITCODE)" -ForegroundColor Red
    exit $LASTEXITCODE
}

Copy-Item (Join-Path $res '*') $classes -Recurse -Force

if (Test-Path $Out) { Remove-Item $Out -Force }
& $jar --create --file $Out -C $classes .

if ($LASTEXITCODE -ne 0) {
    Write-Host "JAR FAILED (exit $LASTEXITCODE)" -ForegroundColor Red
    exit $LASTEXITCODE
}

$size = [math]::Round((Get-Item $Out).Length / 1KB, 1)
Write-Host "BUILD OK: $Out ($size KB)" -ForegroundColor Green
