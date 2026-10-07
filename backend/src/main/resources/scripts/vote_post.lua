-- 调用方持有数据库帖子行锁，快照包含最新接收水位及全部用户状态。
-- Redis 缺失时由完整数据库快照恢复，不再将缺失键解释为零票。
-- KEYS：用户状态哈希、帖子总分、接收版本；ARGV：总分、版本、用户与状态的成对列表。
local changed = 0
if redis.call('GET', KEYS[2]) ~= ARGV[1] or redis.call('GET', KEYS[3]) ~= ARGV[2] then
    changed = 1
end
if redis.call('HLEN', KEYS[1]) ~= (#ARGV - 2) / 2 then
    changed = 1
end
for i = 3, #ARGV, 2 do
    if redis.call('HGET', KEYS[1], ARGV[i]) ~= ARGV[i + 1] then
        changed = 1
    end
end
redis.call('DEL', KEYS[1])
for i = 3, #ARGV, 2 do
    redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
end
redis.call('SET', KEYS[2], ARGV[1])
redis.call('SET', KEYS[3], ARGV[2])
return changed
