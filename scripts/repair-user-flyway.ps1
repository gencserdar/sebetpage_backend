# Removes a failed Flyway V2 entry from user_db so the fixed migration can rerun.
# Usage (from src/microservices): .\scripts\repair-user-flyway.ps1

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

docker compose exec user-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" user_db -e "DELETE FROM flyway_schema_history WHERE version='"'"'2'"'"' AND success=0;"'

Write-Host "Flyway failed V2 row removed. Restart user-service: docker compose up -d user-service"
