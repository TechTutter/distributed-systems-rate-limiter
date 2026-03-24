-- KEYS[1] = rate limiter key
-- ARGV[1] = current time in milliseconds
-- ARGV[2] = window size in milliseconds
-- ARGV[3] = max limit of requests
-- ARGV[4] = unique identifier for the request

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

local window_start = now - window

redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
local requests = tonumber(redis.call('ZCARD', key))

if requests < limit then
    redis.call('ZADD', key, now, member)
    local ttlInSeconds = math.ceil(window / 1000)
    redis.call('EXPIRE', key, ttlInSeconds)
    
    return 1
else
    return 0
end
