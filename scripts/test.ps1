param([string]$JavaHome, [string]$EclipseWorkspace)
. "$PSScriptRoot/common.ps1"
$toolchain = Resolve-PantryPalTools -JavaHome $JavaHome -EclipseWorkspace $EclipseWorkspace
& "$PSScriptRoot/build.ps1" -JavaHome $JavaHome -EclipseWorkspace $EclipseWorkspace
$classes = Join-Path $toolchain.ProjectRoot 'build/classes'
$testOutput = Join-Path $toolchain.ProjectRoot 'build/test-classes'
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $toolchain.ProjectRoot 'test') -Filter '*.java' -Recurse)
$testNames = @($testSources | Where-Object { $_.BaseName -like '*Test' } |
    ForEach-Object { $_.BaseName })
if ($testSources.Count -eq 0 -or $testNames.Count -eq 0) { throw 'No test classes found.' }
$junitVersion = (& $toolchain.Java -cp $toolchain.StudentJar junit.runner.Version | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $junitVersion -ne '4.12') {
    throw "Expected course JUnit 4.12; found '$junitVersion'."
}
New-Item -ItemType Directory -Path $testOutput -Force | Out-Null
$compilePath = "$classes;$($toolchain.StudentJar)"
$sourcePaths = @($testSources | ForEach-Object { $_.FullName })
& $toolchain.Javac --release 11 -encoding UTF-8 -Xlint:rawtypes,unchecked -Werror -cp $compilePath -d $testOutput @sourcePaths
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed (exit $LASTEXITCODE)." }
& $toolchain.Java -cp "$testOutput;$compilePath" org.junit.runner.JUnitCore @testNames
if ($LASTEXITCODE -ne 0) { throw "Tests failed (exit $LASTEXITCODE)." }
