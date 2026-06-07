param(
    [string]$HostName = "Mock Paired PC",
    [string]$Uuid = "MOCK-PC-PAIRING-UUID",
    [string]$HostAddressForClient = "10.0.2.2",
    [int]$HttpPort = 48089,
    [int]$HttpsPort = 48084,
    [int]$PairStatus = 0,
    [int]$HttpsServerInfoStatus = 401,
    [switch]$Background
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$generatedDir = Join-Path $scriptDir ".generated"
New-Item -ItemType Directory -Force -Path $generatedDir | Out-Null

$opensslConfig = Join-Path $generatedDir "openssl-minimal.cnf"
if (!(Test-Path $opensslConfig)) {
    @"
[req]
distinguished_name = dn
prompt = no

[dn]
CN = mock-sunshine
"@ | Set-Content -Encoding ASCII -Path $opensslConfig
}

$env:OPENSSL_CONF = $opensslConfig

function New-TestCertificateIfMissing($KeyPath, $CertPath, $Subject) {
    if ((Test-Path $KeyPath) -and (Test-Path $CertPath)) {
        return
    }

    & openssl req -x509 -newkey rsa:2048 -nodes `
        -keyout $KeyPath `
        -out $CertPath `
        -days 7 `
        -subj $Subject *> $null
}

$serverKey = Join-Path $generatedDir "mock-server-key.pem"
$serverCert = Join-Path $generatedDir "mock-server-cert.pem"
$dbKey = Join-Path $generatedDir "mock-db-key.pem"
$dbCert = Join-Path $generatedDir "mock-db-cert.pem"
$dbCertDer = Join-Path $generatedDir "mock-db-cert.der"

New-TestCertificateIfMissing $serverKey $serverCert "/CN=mock-sunshine-server"
New-TestCertificateIfMissing $dbKey $dbCert "/CN=mock-db-cert"

if (!(Test-Path $dbCertDer)) {
    & openssl x509 -in $dbCert -outform DER -out $dbCertDer *> $null
}

$config = [ordered]@{
    hostName = $HostName
    uniqueId = $Uuid
    hostAddressForClient = $HostAddressForClient
    httpPort = $HttpPort
    httpsPort = $HttpsPort
    pairStatus = $PairStatus
    httpsServerInfoStatus = $HttpsServerInfoStatus
}

$configPath = Join-Path $generatedDir "mock-config.json"
$json = $config | ConvertTo-Json
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($configPath, $json, $utf8NoBom)

$server = Join-Path $scriptDir "server.js"

if ($Background) {
    $stdout = Join-Path $generatedDir "server.out.log"
    $stderr = Join-Path $generatedDir "server.err.log"
    if (Test-Path $stdout) { Clear-Content $stdout }
    if (Test-Path $stderr) { Clear-Content $stderr }

    $process = Start-Process `
        -FilePath "node" `
        -ArgumentList $server `
        -WorkingDirectory $scriptDir `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    "Started mock Sunshine PID=$($process.Id)"
    "stdout: $stdout"
    "stderr: $stderr"
    "db cert: $dbCertDer"
    return
}

& node $server
