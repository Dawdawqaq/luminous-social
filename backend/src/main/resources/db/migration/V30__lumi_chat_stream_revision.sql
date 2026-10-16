-- 单调版本避免较慢的历史读取覆盖流式快照；停止状态沿用现有状态字段。
ALTER TABLE lumi_chat_turns ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
