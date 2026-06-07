param(
    [string]$Package = "com.limelight.vplus_debug",
    [string]$HostName = "Mock Paired PC",
    [string]$Uuid = "MOCK-PC-PAIRING-UUID",
    [string]$HostAddress = "10.0.2.2",
    [int]$HttpPort = 48089,
    [int]$HttpsPort = 48084,
    [string]$CertDerPath = "",
    [switch]$RemoveOnly
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($CertDerPath)) {
    $CertDerPath = Join-Path $scriptDir ".generated\mock-db-cert.der"
}

if (!$RemoveOnly -and !(Test-Path $CertDerPath)) {
    throw "Missing DB certificate DER: $CertDerPath. Run Start-MockSunshine.ps1 once first."
}

function Convert-ToSqlText($Value) {
    return $Value.Replace("'", "''")
}

$deleteSql = "DELETE FROM Computers WHERE UUID='$(Convert-ToSqlText $Uuid)';"

if ($RemoveOnly) {
    $deleteSql | adb shell "run-as $Package sqlite3 databases/computers.db"
    return
}

$bytes = [IO.File]::ReadAllBytes($CertDerPath)
$hex = ($bytes | ForEach-Object { $_.ToString("X2") }) -join ""
$addresses = @{
    manual = @{ address = $HostAddress; port = $HttpPort }
    lastActive = @{ address = $HostAddress; port = $HttpPort }
    httpsPort = $HttpsPort
    ipv6Disabled = $false
} | ConvertTo-Json -Compress

$nameSql = Convert-ToSqlText $HostName
$uuidSql = Convert-ToSqlText $Uuid
$addrSql = Convert-ToSqlText $addresses

$sql = @"
CREATE TABLE IF NOT EXISTS Computers(UUID TEXT PRIMARY KEY, ComputerName TEXT NOT NULL, Addresses TEXT NOT NULL, MacAddress TEXT, ServerCert BLOB);
$deleteSql
INSERT INTO Computers(UUID,ComputerName,Addresses,MacAddress,ServerCert)
VALUES('$uuidSql','$nameSql','$addrSql','00:11:22:33:44:55',X'$hex');
SELECT UUID, ComputerName, Addresses, length(ServerCert) FROM Computers WHERE UUID='$uuidSql';
"@

adb shell "run-as $Package mkdir -p databases" | Out-Null
$sql | adb shell "run-as $Package sqlite3 databases/computers.db"
