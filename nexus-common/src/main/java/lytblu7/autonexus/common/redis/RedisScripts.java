package lytblu7.autonexus.common.redis;

public final class RedisScripts {
    private RedisScripts() {
    }
    
    public static final String UPDATE_PLAYER_LOCATION =
            "local key = KEYS[1]; " +
            "local server = ARGV[1]; " +
            "local name = ARGV[2]; " +
            "local uuid = ARGV[3]; " +
            "local existing = redis.call('GET', key); " +
            "if existing then " +
            "    local decoded = cjson.decode(existing); " +
            "    decoded.currentServer = server; " +
            "    decoded.lastSeenName = name; " +
            "    local encoded = cjson.encode(decoded); " +
            "    redis.call('SET', key, encoded); " +
            "else " +
            "    local newPlayer = {uuid = uuid, lastSeenName = name, currentServer = server, metadata = {}}; " +
            "    local encoded = cjson.encode(newPlayer); " +
            "    redis.call('SET', key, encoded); " +
            "end; " +
            "return 1;";
    
    public static final String UPDATE_PLAYER_METADATA =
            "local key = KEYS[1]; " +
            "local updates = cjson.decode(ARGV[1]); " +
            "local existing = redis.call('GET', key); " +
            "if existing then " +
            "    local decoded = cjson.decode(existing); " +
            "    if not decoded.metadata then decoded.metadata = {} end; " +
            "    for k,v in pairs(updates) do " +
            "        decoded.metadata[k] = v; " +
            "    end; " +
            "    local encoded = cjson.encode(decoded); " +
            "    redis.call('SET', key, encoded); " +
            "else " +
            "    return 0; " +
            "end; " +
            "return 1;";
    
    public static final String INCREMENT_METADATA_ATOMIC =
            "local playerKey = KEYS[1]; " +
            "local historyKey = KEYS[2]; " +
            "local baltopKey = KEYS[3]; " +
            "local field = ARGV[1]; " +
            "local delta = tonumber(ARGV[2]); " +
            "local isBalance = ARGV[3] == \"1\"; " +
            "local serverSource = ARGV[4]; " +
            "local txType = ARGV[5]; " +
            "local otherPlayer = ARGV[6]; " +
            "local playerUuid = ARGV[7]; " +
            "local timestamp = ARGV[8]; " +
            "local reason = ARGV[9]; " +
            "local existing = redis.call('GET', playerKey); " +
            "if not existing then return \"0\" end; " +
            "local obj = cjson.decode(existing); " +
            "if not obj.metadata then obj.metadata = {} end; " +
            "local current = tonumber(obj.metadata[field] or \"0\") or 0; " +
            "local newval = current + delta; " +
            "if delta < 0 and newval < 0 then return \"INSUFFICIENT_FUNDS\" end; " +
            "obj.metadata[field] = tostring(newval); " +
            "redis.call('SET', playerKey, cjson.encode(obj)); " +
            "if isBalance then " +
            "  if baltopKey ~= nil and baltopKey ~= '' then " +
            "    redis.call('ZADD', baltopKey, newval, playerUuid); " +
            "  end; " +
            "  if delta ~= 0 then " +
            "    if not reason or reason == '' then reason = 'SYSTEM' end; " +
            "    local tx = { type = txType, amount = delta, otherPlayer = otherPlayer, timestamp = timestamp, reason = reason }; " +
            "    local txJson = cjson.encode(tx); " +
            "    redis.call('LPUSH', historyKey, txJson); " +
            "    redis.call('LTRIM', historyKey, 0, 49); " +
            "    redis.log(redis.LOG_NOTICE, 'History pushed for ' .. historyKey); " +
            "  end; " +
            "  local update = { playerUuid = playerUuid, newBalance = tostring(newval), serverSource = serverSource, transactionType = txType }; " +
            "  local updateJson = cjson.encode(update); " +
            "  redis.call('PUBLISH', 'autonexus:updates', updateJson); " +
            "end; " +
            "return tostring(newval);";
    
    public static final String TRANSFER_METADATA_ATOMIC =
            "local fromKey = KEYS[1]; " +
            "local toKey = KEYS[2]; " +
            "local field = ARGV[1]; " +
            "local amount = tonumber(ARGV[2]); " +
            "if amount <= 0 then return \"0\" end; " +
            "local fromJson = redis.call('GET', fromKey); " +
            "if not fromJson then return \"INSUFFICIENT_FUNDS\" end; " +
            "local toJson = redis.call('GET', toKey); " +
            "local fromObj = fromJson and cjson.decode(fromJson) or {metadata={}}; " +
            "local toObj = toJson and cjson.decode(toJson) or {metadata={}}; " +
            "if not fromObj.metadata then fromObj.metadata = {} end; " +
            "if not toObj.metadata then toObj.metadata = {} end; " +
            "local fromBal = tonumber(fromObj.metadata[field] or \"0\") or 0; " +
            "local newFrom = fromBal - amount; " +
            "if amount > 0 and newFrom < 0 then return \"INSUFFICIENT_FUNDS\" end; " +
            "local toBal = tonumber(toObj.metadata[field] or \"0\") or 0; " +
            "local newTo = toBal + amount; " +
            "fromObj.metadata[field] = tostring(newFrom); " +
            "toObj.metadata[field] = tostring(newTo); " +
            "redis.call('SET', fromKey, cjson.encode(fromObj)); " +
            "redis.call('SET', toKey, cjson.encode(toObj)); " +
            "return tostring(newFrom);";
}
