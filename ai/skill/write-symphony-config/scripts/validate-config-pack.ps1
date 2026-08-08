# Copyright 2026 17Artist
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

param(
    [Parameter(Mandatory = $true)]
    [string]$PackRoot
)

$ErrorActionPreference = 'Stop'
$java = Get-Command java.exe -ErrorAction SilentlyContinue
if ($null -eq $java) {
    throw 'Java was not found. Install JDK 17 or set JAVA_HOME before validation.'
}
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaDetails = (& $java.Source -XshowSettings:properties -version 2>&1 | Out-String)
$ErrorActionPreference = $previousErrorAction
$versionMatch = [regex]::Match($javaDetails, 'java\.specification\.version\s*=\s*(\d+)')
if (-not $versionMatch.Success -or [int]$versionMatch.Groups[1].Value -lt 17) {
    throw 'Symphony config validation requires JDK 17 or newer.'
}

$resolvedPack = (Resolve-Path -LiteralPath $PackRoot).Path
if (-not (Test-Path -LiteralPath (Join-Path $resolvedPack 'Symphony') -PathType Container)) {
    throw "Config pack must contain a Symphony directory: $resolvedPack"
}

$skillRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $skillRoot '..\..\..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Cannot locate the Symphony repository: $repositoryRoot"
}

$mutexSuffix = ($repositoryRoot -replace '[^A-Za-z0-9]', '_')
$mutex = [System.Threading.Mutex]::new($false, "Local\SymphonyConfigValidation_$mutexSuffix")
$lockTaken = $false
try {
    try {
        $lockTaken = $mutex.WaitOne([TimeSpan]::FromMinutes(5))
    } catch [System.Threading.AbandonedMutexException] {
        $lockTaken = $true
    }
    if (-not $lockTaken) { throw 'Timed out waiting for another Symphony config validation to finish.' }

    Push-Location $repositoryRoot
    try {
        $validationStarted = Get-Date
        & $gradle ':symphony-bukkit:validateConfigPack' "-PsymphonyConfigPack=$resolvedPack" '--no-daemon' '--max-workers=1'
        if ($LASTEXITCODE -ne 0) {
            $result = Join-Path $repositoryRoot 'symphony-bukkit\build\test-results\validateConfigPack\TEST-priv.seventeen.artist.symphony.bukkit.ConfigPackValidationTest.xml'
            $message = $null
            if ((Test-Path -LiteralPath $result -PathType Leaf) -and (Get-Item -LiteralPath $result).LastWriteTime -ge $validationStarted) {
                try {
                    [xml]$report = Get-Content -Raw -Encoding UTF8 -LiteralPath $result
                    $failure = @($report.testsuite.testcase.failure)[0]
                    if ($null -ne $failure) { $message = $failure.message }
                } catch {
                    $message = $null
                }
            }
            if ([string]::IsNullOrWhiteSpace($message)) {
                throw 'Symphony config validation failed. See the Gradle test report for details.'
            }
            throw "Symphony config validation failed: $message"
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($lockTaken) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}

Write-Output "SYMPHONY_CONFIG_PACK_VALID $resolvedPack"
