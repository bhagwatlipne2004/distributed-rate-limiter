package in.ratelimiter;

import in.ratelimiter.config.RateLimiterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimiterProperties.class)
public class DistributedRateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedRateLimiterApplication.class, args);
    }

}
