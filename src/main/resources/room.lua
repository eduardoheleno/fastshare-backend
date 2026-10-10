local action = ARGV[1]
local p = cjson.decode(ARGV[2])
local idle = tonumber(ARGV[3])
local lease = tonumber(ARGV[4])
local maxPeople = tonumber(ARGV[5])
local maxMessages = tonumber(ARGV[6])
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local meta, auth, presence, members, messages, dedup = unpack(KEYS)
local function fail(message)
    return cjson.encode({error = message})
end
if action == 'create' then
    if redis.call('EXISTS', meta) == 1 then return fail('Sala já existe.') end
    redis.call('HSET', meta, 'createdAt', now, 'emptySince', now)
    redis.call('PEXPIRE', meta, idle)
    return '{}'
end
if redis.call('EXISTS', meta) == 0 then return fail('Sala expirada ou inexistente.') end
local expired = redis.call('ZRANGEBYSCORE', presence, '-inf', now, 'WITHSCORES')
local lastExpired = nil
for i = 1, #expired, 2 do
    redis.call('HDEL', members, expired[i])
    lastExpired = tonumber(expired[i + 1])
end
redis.call('ZREMRANGEBYSCORE', presence, '-inf', now)
if redis.call('ZCARD', presence) == 0 and not redis.call('HGET', meta, 'emptySince') then
    redis.call('HSET', meta, 'emptySince', lastExpired or now)
end
local emptySince = tonumber(redis.call('HGET', meta, 'emptySince'))
if emptySince and now >= emptySince + idle then
    redis.call('DEL', unpack(KEYS))
    return fail('Sala expirada ou inexistente.')
end
local function expireKeys()
    local deadline
    local last = redis.call('ZREVRANGE', presence, 0, 0, 'WITHSCORES')
    if #last > 0 then
        deadline = tonumber(last[2]) + idle
    else
        deadline = tonumber(redis.call('HGET', meta, 'emptySince')) + idle
    end
    for _, key in ipairs(KEYS) do redis.call('PEXPIREAT', key, deadline) end
end
local result = {}
if action == 'join' then
    if redis.call('HLEN', auth) >= 200 then return fail('Limite de entradas atingido.') end
    local user = {id = p.id, name = p.name}
    redis.call('HSET', auth, p.token, cjson.encode(user))
    result = {token = p.token, participantId = p.id, name = p.name}
else
    local rawUser = redis.call('HGET', auth, p.token or '')
    if not rawUser then return fail('Acesso inválido.') end
    local user = cjson.decode(rawUser)
    local rawMember = redis.call('HGET', members, p.connectionId or '')
    local member = rawMember and cjson.decode(rawMember) or nil
    if action == 'auth' then
        result = user
    elseif action == 'connect' then
        if member then return fail('Conexão já existe.') end
        if redis.call('ZCARD', presence) >= maxPeople then return fail('Sala lotada.') end
        member = {id = user.id, name = user.name, connectionId = p.connectionId, sharing = false}
        redis.call('HSET', members, p.connectionId, cjson.encode(member))
        redis.call('ZADD', presence, now + lease, p.connectionId)
        redis.call('HDEL', meta, 'emptySince')
    elseif action == 'state' then
        local people = {}
        for _, value in ipairs(redis.call('HVALS', members)) do table.insert(people, cjson.decode(value)) end
        local history = {}
        for _, value in ipairs(redis.call('LRANGE', messages, p.after, p.after + 199)) do
            table.insert(history, cjson.decode(value))
        end
        result = {members = people, messages = history, total = redis.call('LLEN', messages)}
    elseif action == 'disconnect' then
        if member and member.id == user.id then
            redis.call('HDEL', members, p.connectionId)
            redis.call('ZREM', presence, p.connectionId)
            if redis.call('ZCARD', presence) == 0 then redis.call('HSET', meta, 'emptySince', now) end
        end
    else
        if not member or member.id ~= user.id then return fail('Conexão expirada. Reconecte à sala.') end
        if action == 'heartbeat' or action == 'share' then
            redis.call('ZADD', presence, now + lease, p.connectionId)
            if action == 'share' then
                member.sharing = p.sharing
                redis.call('HSET', members, p.connectionId, cjson.encode(member))
            end
        elseif action == 'message' then
            local previous = redis.call('HGET', dedup, user.id .. ':' .. p.clientId)
            if previous then return previous end
            local count = redis.call('LLEN', messages)
            if count >= maxMessages then return fail('Limite de mensagens atingido.') end
            result = {seq = count + 1, clientId = p.clientId, participantId = user.id,
                      name = user.name, text = p.text, sentAt = now}
            local encoded = cjson.encode(result)
            redis.call('RPUSH', messages, encoded)
            redis.call('HSET', dedup, user.id .. ':' .. p.clientId, encoded)
        elseif action == 'signal' then
            local rawTarget = redis.call('HGET', members, p.target)
            if not rawTarget then return fail('Participante indisponível.') end
            local target = cjson.decode(rawTarget)
            if p.kind == 'watch' and not target.sharing then return fail('Transmissão indisponível.') end
            if p.kind == 'offer' and not member.sharing then return fail('Transmissão indisponível.') end
        else
            return fail('Operação inválida.')
        end
    end
end
expireKeys()
return cjson.encode(result)
