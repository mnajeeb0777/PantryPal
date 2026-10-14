param([string]$JavaHome, [string]$EclipseWorkspace)
. "$PSScriptRoot/common.ps1"
$toolchain = Resolve-PantryPalTools -JavaHome $JavaHome -EclipseWorkspace $EclipseWorkspace
& "$PSScriptRoot/build.ps1" -JavaHome $JavaHome -EclipseWorkspace $EclipseWorkspace
& $toolchain.Java -cp (Join-Path $toolchain.ProjectRoot 'build/classes') PantryPalGUI
if ($LASTEXITCODE -ne 0) { throw "PantryPal exited with code $LASTEXITCODE." }
