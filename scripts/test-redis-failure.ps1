
$ErrorActionPreference = "Stop"

$redisContainer = "rate-limiter-redis"
$appUrl = "http://localhost:8080"
$userId = "redis-automation-" + [guid]::NewGuid().ToString("N")

function Get-RedisFailureCount {
    $response = Invoke-WebRequest -UseBasicParsing `
        "$appUrl/actuator/prometheus"

    $match = [regex]::Match(
        $response.Content,
        '(?m)^rate_limiter_redis_failures_total\s+([0-9.]+)'
    )

    if (-not $match.Success) {
        throw "Redis failure metric not found"
    }

    return [double]$match.Groups[1].Value
}

function Invoke-ResourceRequest {
    param([string]$Id)

    try {
        $response = Invoke-WebRequest -UseBasicParsing `
            -Uri "$appUrl/api/resource" `
            -Headers @{"X-User-Id" = $Id}

        return [int]$response.StatusCode
    }
    catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

Write-Host "Reading initial Redis failure count..."

$before = Get-RedisFailureCount

Write-Host "Stopping Redis..."

docker stop $redisContainer | Out-Null

try {
    Start-Sleep -Seconds 2

    Write-Host "Sending request while Redis is unavailable..."

    $status = Invoke-ResourceRequest $userId

    if ($status -ne 200) {
        throw "Expected HTTP 200 with fail-open, got $status"
    }

    Write-Host "Fail-open verified: HTTP $status"

    $after = Get-RedisFailureCount
    $difference = $after - $before

    Write-Host "Redis failures recorded: $difference"

    if ($difference -lt 2) {
        throw "Expected at least 2 Redis failures"
    }

}
finally {
    Write-Host "Restarting Redis..."

    docker start $redisContainer | Out-Null

    $healthy = $false

    for ($i = 0; $i -lt 30; $i++) {
        $health = docker inspect `
            --format '{{.State.Health.Status}}' $redisContainer

        if ($health -eq "healthy") {
            $healthy = $true
            break
        }

        Start-Sleep -Seconds 1
    }

    if (-not $healthy) {
        throw "Redis did not become healthy"
    }
}

Write-Host "Testing application after Redis recovery..."

$recoveryStatus = Invoke-ResourceRequest $userId

if ($recoveryStatus -ne 200) {
    throw "Expected HTTP 200 after recovery, got $recoveryStatus"
}

Write-Host "Redis recovery verified: HTTP $recoveryStatus"

Write-Host "ALL REDIS FAILURE AND RECOVERY TESTS PASSED"
