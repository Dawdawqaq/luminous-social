#!/usr/bin/env bash
# scripts/clean-test-data.sh
# Luminous 精准清除 [E2E_TEST] 与压测数据脚本 (Linux/macOS)

ENV_FILE="${1:-.env.dev}"

echo "=========================================================="
echo "[CLEANUP] Luminous 精准清理 [E2E_TEST] 测试标记数据"
echo "=========================================================="

if [ -f "$ENV_FILE" ]; then
    export $(grep -v '^#' "$ENV_FILE" | xargs)
fi

MYSQL_CONTAINER="${COMPOSE_PROJECT_NAME:-luminous-dev}-mysql"
REDIS_CONTAINER="${COMPOSE_PROJECT_NAME:-luminous-dev}-redis"
DB_NAME="${DB_NAME:-luminous}"
DB_USER="${DB_USER:-luminous}"
DB_PASSWORD="${DB_PASSWORD:-luminous_password}"

CLEAN_SQL="
USE $DB_NAME;
DELETE FROM notifications WHERE receiver_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%') OR actor_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%') OR content_preview LIKE '%[E2E_TEST]%' OR content_preview LIKE '%[BENCHMARK]%';
DELETE FROM user_follows WHERE follower_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%') OR following_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%');
DELETE FROM comments WHERE content LIKE '%[E2E_TEST]%' OR content LIKE '%[BENCHMARK]%';
DELETE FROM post_votes WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%');
DELETE FROM bookmarks WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%');
DELETE FROM posts WHERE title LIKE '%[E2E_TEST]%' OR title LIKE '%[BENCHMARK]%';
DELETE FROM communities WHERE name LIKE 'e2e-%' OR name LIKE 'benchmark-%';
DELETE FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%';
"

echo "1. 执行 MySQL 测试数据精准清理 (包含 notifications 与 user_follows)..."
docker exec -i "$MYSQL_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASSWORD" -e "$CLEAN_SQL" 2>/dev/null

echo "2. 清理 Redis 测试 Key 与临时状态 (限定 luminous 命名空间)..."
docker exec -i "$REDIS_CONTAINER" sh -c "for key in \$(redis-cli --scan --pattern 'luminous:*e2e*'); do redis-cli del \"\$key\"; done; for key in \$(redis-cli --scan --pattern 'luminous:*bench*'); do redis-cli del \"\$key\"; done; for key in \$(redis-cli --scan --pattern 'luminous:ratelimit:*'); do redis-cli del \"\$key\"; done" 2>/dev/null

echo "[OK] [E2E_TEST] 标记数据清理完成，开发环境核心数据完整保留！"
