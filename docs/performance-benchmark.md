# Performance Benchmark Report

## 1. Overview

This document records performance tests conducted on the Distributed Rate Limiter using k6.

The objective was to evaluate request throughput, response latency, rate-limit enforcement, and the impact of adding a second Spring Boot application instance.

## 2. Test Environment

| Component | Configuration |
|---|---|
| Application | Spring Boot |
| Language | Java 21 |
| Rate Limiting Algorithm | Token Bucket |
| Distributed Storage | Redis 7 |
| Deployment | Docker Compose |
| Load Testing | k6 v2.3.0 |
| Application Instances | 1 and 2 |
| Test Duration | 55 seconds per run |
| Rate Limit Capacity | 100 tokens |
| Token Refill Rate | 10 tokens/second |

Host hardware specifications were not recorded during these tests.

## 3. Test Methodology

The benchmark used k6 with the following load profile:

| Stage | Duration | Target VUs |
|---|---:|---:|
| Ramp-up | 15 seconds | 100 / 500 / 1,000 |
| Sustained load | 30 seconds | 100 / 500 / 1,000 |
| Ramp-down | 10 seconds | 0 |

Each virtual user sent HTTP GET requests to:

`/api/resource`

Requests included the `X-User-Id` header.

All virtual users shared the same user identifier to test concurrent access to a shared Redis token bucket.

HTTP 200 and HTTP 429 were treated as expected responses.

## 4. Single-Instance Results

| Virtual Users | Total Requests | Throughput (RPS) | Average Latency | P95 Latency |
|---:|---:|---:|---:|---:|
| 100 | 293,574 | 5,337 | 14.28 ms | 26.86 ms |
| 500 | 382,811 | 6,960 | 55.37 ms | 83.56 ms |
| 1,000 | 226,035 | 4,109 | 187.94 ms | 422.01 ms |

### Observations

- Throughput increased between 100 and 500 virtual users.
- At 1,000 virtual users, throughput decreased.
- Response latency increased significantly at higher concurrency.
- No unexpected HTTP responses were recorded.

## 5. Two-Instance Results

Both Spring Boot instances shared the same Redis service.

Requests were distributed between the two application instances using virtual-user IDs.

| Virtual Users | Total Requests | Throughput (RPS) | Average Latency | P95 Latency |
|---:|---:|---:|---:|---:|
| 1,000 | 285,694 | 5,194 | 148.70 ms | 292.91 ms |
| 1,000 | 379,663 | 6,902 | 111.87 ms | 177.03 ms |

The second run also measured:

| Metric | Result |
|---|---:|
| P99 Latency | 209.08 ms |
| Maximum Latency | 789.69 ms |
| HTTP Failures | 0% |
| Unexpected Responses | 0 |

The final two-instance benchmark run passed both configured latency thresholds:

- P95 < 500 ms
- P99 < 1,000 ms

## 6. Single vs Two Application Instances

Comparison at 1,000 virtual users:

| Metric | One Instance | Two Instances (Final Run) |
|---|---:|---:|
| Throughput | 4,109 RPS | 6,902 RPS |
| Average Latency | 187.94 ms | 111.87 ms |
| P95 Latency | 422.01 ms | 177.03 ms |

The final two-instance run showed approximately:

- 68% higher observed throughput.
- 40.5% lower average latency.
- 58% lower P95 latency.

These comparisons are indicative rather than definitive because the two-instance test was repeated and produced different results. Multiple controlled repetitions are required for a reliable scalability conclusion.

## 7. Rate Limiting Validation

The benchmark used:

- Bucket capacity: 100 tokens.
- Refill rate: 10 tokens per second.
- Shared user identifier across virtual users.

Across the 55-second tests, approximately 646–649 requests were allowed.

The remaining requests were rejected with HTTP 429.

This demonstrates that both application instances accessed shared rate-limit state instead of maintaining independent in-memory buckets.

## 8. Key Findings

1. Redis-backed shared state maintained rate-limit enforcement across application instances.
2. The system handled increasing concurrency without unexpected HTTP responses in the recorded tests.
3. A second application instance improved observed throughput and reduced latency in the final comparison.
4. Latency increased significantly under high concurrency.
5. Additional profiling is needed to identify the precise performance bottleneck.

## 9. Limitations

- Tests were conducted on a local Docker environment.
- Host hardware specifications were not recorded.
- Benchmark repetitions were limited.
- CPU utilization was inspected after tests rather than continuously captured during them.
- The load generator and application shared the same host machine.
- Results should not be interpreted as guaranteed production capacity.

## 10. Conclusion

The benchmark demonstrates the basic performance and distributed rate-limiting behavior of the system under concurrent load.

The two-instance configuration showed better observed throughput and latency than the single-instance configuration in the final comparison.

Further testing on dedicated infrastructure, with repeated benchmark runs and resource monitoring, is necessary before making production performance guarantees.
