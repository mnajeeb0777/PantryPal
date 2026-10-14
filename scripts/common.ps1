# Shared tool discovery; machine-specific paths stay outside project metadata.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-PantryPalTools {
    param([string]$JavaHome, [string]$EclipseWorkspace)

    if (-not $EclipseWorkspace) {
        $EclipseWorkspace = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'eclipse-workspace'
    }
    $studentJar = Join-Path $EclipseWorkspace 'CS2-Support/student.jar'
    if (-not (Test-Path -LiteralPath $studentJar -PathType Leaf)) {
        throw "Course student.jar not found. Pass -EclipseWorkspace with your course workspace."
    }

    $candidates = @()
    if ($JavaHome) {
        $candidates = @($JavaHome)
    }
    else {
        if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
        $plugins = Join-Path $env:USERPROFILE 'eclipse/plugins'
        if (Test-Path -LiteralPath $plugins) {
            $candidates += @(Get-ChildItem -LiteralPath $plugins -Directory -Filter '*openjdk*17*' |
                ForEach-Object { Join-Path $_.FullName 'jre' })
        }
        $javaCommand = Get-Command java -ErrorAction SilentlyContinue
        if ($javaCommand) {
            $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
        }
    }

    foreach ($candidate in $candidates) {
        $java = Join-Path $candidate 'bin/java.exe'
        $javac = Join-Path $candidate 'bin/javac.exe'
        if (-not (Test-Path -LiteralPath $java) -or
            -not (Test-Path -LiteralPath $javac)) { continue }
        $runtimeVersion = (& $java --version 2>&1 | Out-String)
        $runtimeExit = $LASTEXITCODE
        $compilerVersion = (& $javac --version 2>&1 | Out-String)
        if ($runtimeExit -eq 0 -and $LASTEXITCODE -eq 0 -and
            $runtimeVersion -match '(?m)^(openjdk|java) 17[. ]' -and
            $compilerVersion -match '^javac 17[. ]') {
            return [PSCustomObject]@{
                Java = $java
                Javac = $javac
                StudentJar = (Resolve-Path -LiteralPath $studentJar).Path
                CompilerVersion = $compilerVersion.Trim()
                ProjectRoot = Split-Path $PSScriptRoot -Parent
            }
        }
    }
    throw 'A full JDK 17 is required. Pass -JavaHome with its installation directory.'
}
