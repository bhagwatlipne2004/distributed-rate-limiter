
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Custom metrics
const allowedRequests = new Counter('rate_limit_allowed');
const rejectedRequests = new Counter('rate_limit_rejected');
const unexpectedResponses = new Counter('unexpected_responses');

// Load test configuration
export const options = {
    stages: [
        { duration: '15s', target: 1000 },
        { duration: '30s', target: 1000 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_duration: [
            'p(95)<500',
            'p(99)<1000',
        ],
    },
};

// Configure application instances through environment variables.
// Single instance:
// BASE_URLS=http://localhost:8080
//
// Two instances:
// BASE_URLS=http://localhost:8080,http://localhost:8081

const BASE_URLS = (
    __ENV.BASE_URLS || 'http://localhost:8080'
).split(',').map(url => url.trim()).filter(Boolean);

const USER_ID = 'k6-benchmark';

// Treat HTTP 200 and 429 as expected responses.
http.setResponseCallback(http.expectedStatuses(200, 429));

export default function () {

    // Distribute virtual users across application instances.
    const instanceIndex = (__VU - 1) % BASE_URLS.length;
    const baseUrl = BASE_URLS[instanceIndex];

    const response = http.get(
        `${baseUrl}/api/resource`,
        {
            headers: {
                'X-User-Id': USER_ID,
            },
        }
    );

    // Classify responses.
    if (response.status === 200) {
        allowedRequests.add(1);

    } else if (response.status === 429) {
        rejectedRequests.add(1);

    } else {
        unexpectedResponses.add(1);
    }

    // Validate expected HTTP responses.
    check(response, {
        'status is 200 or 429': (r) =>
            r.status === 200 || r.status === 429,
    });
}
