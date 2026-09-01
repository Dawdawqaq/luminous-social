#!/usr/bin/env bash
# scripts/init-db.sh
# Luminous 数据库自动化就绪检查与初始化脚本 (Linux/macOS)

ENV_FILE="${1:-.env.dev}"

echo "=========================================================="
echo "[DATABASE] Luminous 数据库自动化初始化与就绪检测"
echo "=========================================================="

if [ -f "$ENV_FILE" ]; then
    export $(grep -v '^#' "$ENV_FILE" | xargs)
fi

CONTAINER_NAME="${COMPOSE_PROJECT_NAME:-luminous-dev}-mysql"
DB_USER="${DB_USER:-luminous}"
DB_PASSWORD="${DB_PASSWORD:-luminous_password}"
DB_NAME="${DB_NAME:-luminous}"

echo "[CHECK] 检查数据库容器健康状态: $CONTAINER_NAME ..."

RETRIES=30
READY=false

for i in $(seq 1 $RETRIES); do
    HEALTH=$(docker inspect --format="{{json .State.Health.Status}}" "$CONTAINER_NAME" 2>/dev/null)
    if [[ "$HEALTH" =~ "healthy" ]]; then
        READY=true
        break
    fi
    echo "[WAIT] 等待 MySQL 容器就绪 ($i/$RETRIES)..."
    sleep 2
done

if [ "$READY" = true ]; then
    echo "[SUCCESS] MySQL 容器已处于 Healthy 状态！"
    TABLE_COUNT=$(docker exec -i "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name IN ('users', 'posts', 'notifications');" 2>/dev/null | grep -E '^[0-9]+' || echo "0")
    if [ "$TABLE_COUNT" -ge 3 ]; then
        echo "[SUCCESS] Flyway 核心业务表 (users, posts, notifications) 已确认就绪！"
    else
        echo "[INFO] 核心业务表待 Spring Boot 启动自动执行 Flyway 迁移。"
    fi
else
    echo "[WARN] MySQL 容器未能及时进入 Healthy 状态，请检查日志: docker logs $CONTAINER_NAME"
    exit 1
fi
