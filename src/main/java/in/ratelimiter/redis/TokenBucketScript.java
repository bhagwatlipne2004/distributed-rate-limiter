package in.ratelimiter.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TokenBucketScript {

    private final DefaultRedisScript<List> script;

    public TokenBucketScript() {

        script = new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource("scripts/token_bucket.lua")
        );

        script.setResultType(List.class);
    }

    public DefaultRedisScript<List> getScript() {
        return script;
    }
}