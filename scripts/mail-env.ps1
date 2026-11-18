function Import-GouyanMailEnvironment {
    param([Parameter(Mandatory=$true)][string]$RuntimeDirectory)
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw 'Encrypted local SMTP settings require Windows. Use SMTP environment variables on macOS.'
    }
    if ($env:SMTP_ENABLED -eq 'false') { return }
    if ($env:SMTP_ENABLED -eq 'true' -and $env:SMTP_HOST -and $env:SMTP_USERNAME -and $env:SMTP_PASSWORD) { return }
    $settingsPath = Join-Path $RuntimeDirectory 'smtp-settings.json'
    $credentialPath = Join-Path $RuntimeDirectory 'smtp-credential.xml'
    if (-not (Test-Path -LiteralPath $settingsPath)) { return }
    if (-not (Test-Path -LiteralPath $credentialPath)) { throw 'Local SMTP credential file is missing.' }
    $settings = Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $credential = Import-Clixml -LiteralPath $credentialPath
    if ($credential -isnot [System.Management.Automation.PSCredential]) { throw 'Invalid SMTP credential file.' }
    $env:SMTP_ENABLED = 'true'
    $env:SMTP_HOST = [string]$settings.host
    $env:SMTP_PORT = [string]$settings.port
    $env:SMTP_USERNAME = $credential.UserName
    $env:SMTP_FROM = $credential.UserName
    $env:SMTP_PASSWORD = $credential.GetNetworkCredential().Password
    $env:SMTP_AUTH = 'true'
    $env:SMTP_SSL = 'true'
    $env:SMTP_STARTTLS = 'false'
}
