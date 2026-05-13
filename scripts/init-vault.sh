#!/usr/bin/env bash
# Initializes the Vault dev instance with example secrets used by the Config Server PoC.
# Assumes Vault runs via docker-compose (container: vault-poc) at http://127.0.0.1:8200
# with dev root token "root-token-poc".
set -euo pipefail

CONTAINER="${VAULT_CONTAINER:-vault-poc}"
VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root-token-poc}"

vault_exec() {
    docker exec \
        -e VAULT_ADDR="$VAULT_ADDR" \
        -e VAULT_TOKEN="$VAULT_TOKEN" \
        "$CONTAINER" vault "$@"
}

echo "==> Vault status"
vault_exec status

echo "==> Writing shared 'application' secrets"
vault_exec kv put secret/application \
    app.global.message="Hello from Vault (shared)" \
    app.global.owner="platform-team"

echo "==> Writing 'sample-service' (default profile) secrets"
vault_exec kv put secret/sample-service \
    spring.datasource.username=sample_user \
    spring.datasource.password=s3cr3t-default \
    sample.api.key=API-KEY-DEFAULT

echo "==> Writing 'sample-service,dev' secrets"
vault_exec kv put "secret/sample-service,dev" \
    spring.datasource.username=sample_user_dev \
    spring.datasource.password=s3cr3t-dev \
    sample.api.key=API-KEY-DEV

echo "==> Writing 'sample-service,prod' secrets"
vault_exec kv put "secret/sample-service,prod" \
    spring.datasource.username=sample_user_prod \
    spring.datasource.password=s3cr3t-prod \
    sample.api.key=API-KEY-PROD

echo "==> Done."
