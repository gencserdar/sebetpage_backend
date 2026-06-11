# Build all compose services one at a time with shared Maven cache (see Dockerfiles).
# Parallel `docker compose build` hammers Maven Central and often breaks large JAR downloads.
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_PARALLEL_LIMIT = "1"
Set-Location $PSScriptRoot\..

if ($args.Count -gt 0) {
    docker compose build @args
} else {
    docker compose build
}
