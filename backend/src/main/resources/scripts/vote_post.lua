-- Atomic post vote operation in Redis
-- KEYS[1] = vote record key: "vote:post:{postId}:{userId}"
-- KEYS[2] = post vote count key: "stat:post:votes:{postId}" 
-- ARGV[1] = new vote value (-1, 0, or 1)
-- Returns: {oldValue, delta, newTotalVotes}

local oldVal = tonumber(redis.call('GET', KEYS[1]) or '0') or 0
local newVal = tonumber(ARGV[1])
local delta = newVal - oldVal

if newVal == 0 then
    redis.call('DEL', KEYS[1])
else
    redis.call('SET', KEYS[1], tostring(newVal))
end

if delta ~= 0 then
    redis.call('INCRBY', KEYS[2], delta)
end

local totalVotes = tonumber(redis.call('GET', KEYS[2]) or '0') or 0
return {oldVal, delta, totalVotes}
