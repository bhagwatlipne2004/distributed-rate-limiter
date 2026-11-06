
-- Distributed Token Bucket Rate Limiter
--
-- KEYS[1] = Redis bucket key
--
-- ARGV[1] = Bucket capacity
-- ARGV[2] = Refill rate (tokens per second)
-- ARGV[3] = Request cost

local key = KEYS[1]

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local request_cost = tonumber(ARGV[3])

-- Validate arguments
if not capacity or capacity <= 0 then
    return redis.error_reply("Invalid bucket capacity")
end

if not refill_rate or refill_rate <= 0 then
    return redis.error_reply("Invalid refill rate")
end

if not request_cost or request_cost <= 0 or request_cost > capacity then
    return redis.error_reply("Invalid request cost")
end

-- Get authoritative time from Redis
local redis_time = redis.call("TIME")

local seconds = tonumber(redis_time[1])
local microseconds = tonumber(redis_time[2])

local now = (seconds * 1000) + math.floor(microseconds / 1000)

-- Read existing bucket state
local bucket = redis.call(
    "HMGET",
    key,
    "tokens",
    "last_refill_timestamp"
)

local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Initialize bucket for a new user
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- Calculate refill
local elapsed_time = math.max(0, now - last_refill)

local tokens_to_add =
    (elapsed_time / 1000) * refill_rate

tokens = math.min(
    capacity,
    tokens + tokens_to_add
)

last_refill = now

-- Check request
local allowed = 0
local retry_after = 0

if tokens >= request_cost then

    tokens = tokens - request_cost
    allowed = 1

else

    local tokens_needed = request_cost - tokens

    retry_after = math.ceil(
        tokens_needed / refill_rate
    )

end

-- Save updated bucket state
redis.call(
    "HSET",
    key,
    "tokens", tokens,
    "last_refill_timestamp", last_refill
)

-- Keep inactive buckets for one hour
redis.call("EXPIRE", key, 3600)

-- Return decision, remaining tokens and retry duration
return {
    allowed,
    tokens,
    retry_after
}
