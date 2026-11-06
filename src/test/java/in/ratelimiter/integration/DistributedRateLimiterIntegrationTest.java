
package in.ratelimiter.integration;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistributedRateLimiterIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpResponse<String> sendRequest(
            String url,
            String userId
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-User-Id", userId)
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void shouldShareRateLimitAcrossApplicationInstances()
            throws Exception {

        String userId = "distributed-integration-test";

        String app1 = "http://localhost:8080/api/resource";
        String app2 = "http://localhost:8081/api/resource";

        // First five requests go to instance 1.
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response =
                    sendRequest(app1, userId);

            assertEquals(200, response.statusCode());
        }

        // Sixth request goes to instance 2.
        HttpResponse<String> response =
                sendRequest(app2, userId);

        assertEquals(
                429,
                response.statusCode(),
                "Instance 2 should reject the request because "
                        + "instance 1 already consumed the shared bucket"
        );
    }
}
