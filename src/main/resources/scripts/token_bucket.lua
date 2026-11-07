
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

-- Maximum supported Redis key expiration: 30 days
local MAX_TTL = 2592000
local MIN_TTL = 3600
local TTL_SAFETY_MARGIN = 60

-- Validate bucket capacity
if not capacity or
   capacity <= 0 or
   capacity == math.huge or
   capacity == -math.huge or
   capacity ~= capacity then

    return redis.error_reply("Invalid bucket capacity")
end

-- Validate refill rate
if not refill_rate or
   refill_rate <= 0 or
   refill_rate == math.huge or
   refill_rate == -math.huge or
   refill_rate ~= refill_rate then

    return redis.error_reply("Invalid refill rate")
end

-- Validate request cost
if not request_cost or
   request_cost <= 0 or
   request_cost > capacity or
   request_cost == math.huge or
   request_cost == -math.huge or
   request_cost ~= request_cost then

    return redis.error_reply("Invalid request cost")
end

-- Calculate required full refill time
local refill_time = math.ceil(capacity / refill_rate)

-- Reject configurations that exceed supported TTL
if refill_time > (MAX_TTL - TTL_SAFETY_MARGIN) then
    return redis.error_reply(
        "Bucket refill time exceeds maximum supported TTL"
    )
end

-- Get authoritative time from Redis
local redis_time = redis.call("TIME")

local seconds = tonumber(redis_time[1])
local microseconds = tonumber(redis_time[2])

local now = (seconds * 1000) +
            math.floor(microseconds / 1000)

-- Read existing bucket state
local bucket = redis.call(
    "HMGET",
    key,
    "tokens",
    "last_refill_timestamp"
)

local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Initialize bucket for a new user or incomplete state
if tokens == nil or last_refill == nil then
    tokens = capacity
    last_refill = now
end

-- Calculate elapsed time
local elapsed_time = math.max(
    0,
    now - last_refill
)

-- Calculate token refill
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

-- Calculate dynamic expiration
-- Keep the bucket until it can refill completely,
-- with a minimum one-hour TTL and a 60-second safety margin.

local ttl = math.max(
    MIN_TTL,
    refill_time + TTL_SAFETY_MARGIN
)

redis.call("EXPIRE", key, ttl)

-- Return decision, remaining tokens and retry duration.
-- Return remaining tokens as a string to preserve fractional precision.
return {
    allowed,
    tostring(tokens),
    retry_after
}
