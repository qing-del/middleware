local fields = {
  'schema_version', 'client_id', 'redirect_uri', 'scopes', 'code_challenge', 'code_challenge_method',
  'original_socket_address', 'oauth_state', 'issued_at_epoch_millis', 'expires_at_epoch_millis',
  'user_id', 'username', 'role_id', 'password_hash', 'email_present', 'email', 'extra_grant_types', 'status'
}

if #KEYS ~= 2 or #ARGV ~= 39 then return -1 end
if not string.match(ARGV[1], '^[1-9][0-9]*$') then return -1 end
if type(ARGV[2]) ~= 'string' or #ARGV[2] ~= 43 or not string.match(ARGV[2], '^[A-Za-z0-9_-]+$') then return -1 end
if not string.match(ARGV[3], '^[1-9][0-9]*$') then return -1 end
if KEYS[1] ~= 'oauth2:auth_code:{' .. ARGV[2] .. '}' then return -1 end
if KEYS[2] ~= 'user:auth_code:{' .. ARGV[3] .. '}' then return -1 end
for index, field in ipairs(fields) do
  if ARGV[3 + index * 2 - 1] ~= field then return -1 end
end
if ARGV[5] ~= '1' or ARGV[7] ~= 'core_agent' or ARGV[25] ~= ARGV[3] then return -1 end

local old_code = redis.call('GET', KEYS[2])
if old_code and #old_code == 43 and string.match(old_code, '^[A-Za-z0-9_-]+$') then
  redis.call('DEL', 'oauth2:auth_code:{' .. old_code .. '}')
end
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], unpack(ARGV, 4))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[1])
return 1
