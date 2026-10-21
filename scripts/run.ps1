param([string]$JavaHome, [string]$EclipseWorkspace, [string]$DataFile)
. "$PSScriptRoot/common.ps1"
$toolchain = Resolve-PantryPalTools -JavaHome $JavaHome -EclipseWorkspace $EclipseWorkspace
& "$PSScriptRoot/build.ps1" -JavaHome $JavaHome -EclipseWorkspace $EclipseWorkspace
$javaOptions = @()
$javaOptions += "-Dpantrypal.priceConfig=$(Join-Path $toolchain.ProjectRoot '.env.local')"
if ($DataFile) { $javaOptions += "-Dpantrypal.dataFile=$DataFile" }
& $toolchain.Java @javaOptions -cp (Join-Path $toolchain.ProjectRoot 'build/classes') PantryPalGUI
if ($LASTEXITCODE -ne 0) { throw "PantryPal exited with code $LASTEXITCODE." }
