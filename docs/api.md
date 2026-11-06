
# Distributed Rate Limiter API Documentation

## 1. Overview

The Distributed Rate Limiter exposes an HTTP endpoint that controls access to a resource using the Token Bucket algorithm.

Rate-limit policies and token bucket state are stored in Redis.

## 2. Base URLs

Two Spring Boot instances are available:

| Instance | Base URL |
|---|---|
| Application 1 | http://localhost:8080 |
| Application 2 | http://localhost:8081 |

Both instances share the same Redis state.

## 3. Access Resource

### Endpoint

`GET /api/resource`

### Request Headers

| Header | Required | Description |
|---|---|---|
| X-User-Id | Yes | Identifies the user whose rate limit should be checked |

### Example Request

```http
GET /api/resource HTTP/1.1
Host: localhost:8080
X-User-Id: user123
```

### cURL

```bash
curl -i http://localhost:8080/api/resource \
  -H "X-User-Id: user123"
```

The same request can be sent to port 8081.

## 4. Response Examples

### 200 OK — Request Allowed

Response body:

```text
Request allowed!
```

Response headers:

```http
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
```

The values above are illustrative; actual values depend on the user's configured policy and bucket state.

### 429 Too Many Requests — Rate Limit Exceeded

Response body:

```text
Rate limit exceeded!
```

Response headers:

```http
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
Retry-After: 1
```

The example header values are illustrative.

`Retry-After` indicates how many seconds the client should wait before retrying.

### 503 Service Unavailable — Redis Failure

When Redis is unavailable and the configured failure strategy is `fail-closed`, the application returns HTTP 503.

When the strategy is `fail-open`, the application allows the request to proceed.

## 5. Rate-Limit Headers

| Header | Description |
|---|---|
| X-RateLimit-Limit | Maximum bucket capacity |
| X-RateLimit-Remaining | Remaining available tokens |
| Retry-After | Retry delay in seconds, returned when a request is rejected |

## 6. Rate-Limit Policy

The rate-limit policy determines:

- Plan name
- Bucket capacity
- Token refill rate

The policy is retrieved from Redis using the user's identifier.

Redis key format:

`rate_policy:USER:{userId}`

## 7. Distributed Behavior

The same user identifier is used across application instances.

For example:

1. Send requests to application 1 on port 8080.
2. Continue sending requests to application 2 on port 8081.
3. Both instances use the same Redis token bucket.
4. The shared rate limit remains enforced across instances.

## 8. Error Handling

| Scenario | Expected behavior |
|---|---|
| Request within limit | HTTP 200 |
| Bucket exhausted | HTTP 429 |
| Redis unavailable, fail-open | Request allowed |
| Redis unavailable, fail-closed | HTTP 503 |

## 9. Observability

Prometheus metrics are available through:

`GET /actuator/prometheus`

Application health is available through:

`GET /actuator/health`
