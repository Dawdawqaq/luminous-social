-- 已提前应用旧版 V28 草案的环境，在核查并修复草案校验记录后统一机器人内部标识。
-- 只操作固定编号且明确标记为 BOT 的系统账号，不修改任何普通用户的名称或身份。
UPDATE users SET username='lumi.bot'
WHERE id=880000000000000001 AND account_type='BOT' AND username='Lumi';
