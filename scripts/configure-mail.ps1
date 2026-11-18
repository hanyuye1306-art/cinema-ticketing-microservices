param()
$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Use SMTP environment variables on macOS.' }
$root = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $root '.runtime'
[void](New-Item -ItemType Directory -Path $runtime -Force)
$savedUser = $env:SMTP_USERNAME
$savedPassword = $env:SMTP_PASSWORD
try {
    $Host.UI.RawUI.WindowTitle = 'QQ SMTP 配置 v2'
    Write-Host 'QQ SMTP setup v2 / QQ 邮箱配置' -ForegroundColor Cyan
    Write-Host '请先在 QQ 邮箱设置中开启 SMTP 并生成授权码。不要输入 QQ 登录密码。'
    Write-Host '授权码将在本机加密保存，不会写入 Git 或显示在日志中。'
    $classpathFile = Join-Path $runtime 'mail-classpath.txt'
    if (-not (Test-Path -LiteralPath $classpathFile)) {
        Push-Location (Join-Path $root 'gouyan-cloud/gouyan-auth')
        try {
            & mvn.cmd -q dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
            if ($LASTEXITCODE -ne 0) { throw 'Unable to prepare mail dependencies.' }
        } finally { Pop-Location }
    }
    $classpath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
    & javac.exe -source 8 -target 8 -proc:none -encoding UTF-8 -cp $classpath -d $runtime (Join-Path $PSScriptRoot 'SmtpConnectionCheck.java')
    if ($LASTEXITCODE -ne 0) { throw 'Unable to compile SMTP connection check.' }
    do {
        $address = (Read-Host '请输入发件 QQ 邮箱（例如 123456@qq.com）').Trim()
    } while ($address -notmatch '^[^@\s]+@(qq|foxmail)\.com$')
    do {
        $secret = Read-Host '请输入 SMTP 授权码（输入不会显示）' -AsSecureString
        $credential = [System.Management.Automation.PSCredential]::new($address, $secret)
        $env:SMTP_USERNAME = $address
        $env:SMTP_PASSWORD = $credential.GetNetworkCredential().Password.Trim().Replace(' ', '')
        Write-Host '正在检查加密连接和授权信息，不发送邮件...'
        & java.exe -cp "$runtime;$classpath" SmtpConnectionCheck
        $verified = $LASTEXITCODE -eq 0
        if (-not $verified) {
            if ((Read-Host '配置未通过，是否重新输入授权码？[y/N]') -ne 'y') { throw 'SMTP configuration was not saved.' }
        }
    } while (-not $verified)
    $normalized = [System.Security.SecureString]::new()
    foreach ($character in $env:SMTP_PASSWORD.ToCharArray()) { $normalized.AppendChar($character) }
    $normalized.MakeReadOnly()
    $credential = [System.Management.Automation.PSCredential]::new($address, $normalized)
    $credential | Export-Clixml -LiteralPath (Join-Path $runtime 'smtp-credential.xml') -Encoding UTF8
    @{ host = 'smtp.qq.com'; port = 465; verifiedAt = (Get-Date).ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'smtp-settings.json') -Encoding UTF8
    Write-Host '配置已验证并加密保存。重新启动认证服务后生效。' -ForegroundColor Green
} catch {
    Write-Host ('配置未完成：' + $_.Exception.Message) -ForegroundColor Red
} finally {
    $env:SMTP_USERNAME = $savedUser
    $env:SMTP_PASSWORD = $savedPassword
    [void](Read-Host '按 Enter 关闭窗口')
}
