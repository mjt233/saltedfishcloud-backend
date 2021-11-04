local o = tonumber(redis.call('get',KEYS[1]))
local min = tonumber(ARGV[2])
local step = tonumber(ARGV[1])
local target = o - step
if(target >= min) then
    redis.call('set',KEYS[1], target)
    return target
end
