param([string]$JavaHome, [string]$EclipseWorkspace)
. "$PSScriptRoot/common.ps1"
$toolchain = Resolve-PantryPalTools -JavaHome $JavaHome -EclipseWorkspace $EclipseWorkspace
$output = Join-Path $toolchain.ProjectRoot 'build/classes'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $toolchain.ProjectRoot 'src') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw 'No Java source files found.' }
New-Item -ItemType Directory -Path $output -Force | Out-Null
Write-Host "Compiler: $($toolchain.CompilerVersion); source/API target: Java 11"
& $toolchain.Javac --release 11 -encoding UTF-8 -Xlint:rawtypes,unchecked -Werror -d $output @sources
if ($LASTEXITCODE -ne 0) { throw "Source compilation failed (exit $LASTEXITCODE)." }
Write-Host "PASS: compiled $($sources.Count) source file(s)."
