<#
.SYNOPSIS
    Initializes the Vault dev instance with example secrets used by the Config Server PoC.

.DESCRIPTION
    Assumes Vault is running via docker-compose (container name: vault-poc) on http://127.0.0.1:8200
    with the dev root token "root-token-poc".

    Writes sample KV v2 secrets at:
      - secret/application                  (shared across all applications)
      - secret/sample-service               (default profile)
      - secret/sample-service,dev           (dev profile)
      - secret/sample-service,prod          (prod profile)
#>

$ErrorActionPreference = 'Stop'

$Container = 'vault-poc'
$VaultAddr = 'http://127.0.0.1:8200'
$VaultToken = 'root-token-poc'

function Invoke-Vault {
    param([Parameter(Mandatory)][string[]]$Args)
    docker exec `
        -e VAULT_ADDR=$VaultAddr `
        -e VAULT_TOKEN=$VaultToken `
        $Container vault @Args
    if ($LASTEXITCODE -ne 0) { throw "vault command failed: $($Args -join ' ')" }
}

Write-Host "==> Vault status" -ForegroundColor Cyan
Invoke-Vault @('status')

Write-Host "==> Writing shared 'application' secrets" -ForegroundColor Cyan
Invoke-Vault @('kv', 'put', 'secret/application',
    'app.global.message=Hello from Vault (shared)',
    'app.global.owner=platform-team')

Write-Host "==> Writing 'sample-service' (default profile) secrets" -ForegroundColor Cyan
Invoke-Vault @('kv', 'put', 'secret/sample-service',
    'spring.datasource.username=sample_user',
    'spring.datasource.password=s3cr3t-default',
    'sample.api.key=API-KEY-DEFAULT')

Write-Host "==> Writing 'sample-service,dev' secrets" -ForegroundColor Cyan
Invoke-Vault @('kv', 'put', 'secret/sample-service,dev',
    'spring.datasource.username=sample_user_dev',
    'spring.datasource.password=s3cr3t-dev',
    'sample.api.key=API-KEY-DEV')

Write-Host "==> Writing 'sample-service,prod' secrets" -ForegroundColor Cyan
Invoke-Vault @('kv', 'put', 'secret/sample-service,prod',
    'spring.datasource.username=sample_user_prod',
    'spring.datasource.password=s3cr3t-prod',
    'sample.api.key=API-KEY-PROD')

Write-Host "==> Done. Verify with:" -ForegroundColor Green
Write-Host "    docker exec -e VAULT_ADDR=$VaultAddr -e VAULT_TOKEN=$VaultToken $Container vault kv get secret/sample-service"
