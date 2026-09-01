#!/usr/bin/env bash
# scripts/stop-prod-lab.sh
# Luminous 生产模拟实验室停止脚本 (安全保护持久化卷)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR" || exit 1

echo "[STOP] 正在停止 Luminous 生产模拟实验室 (保留数据卷)..."

docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod down

echo "[SUCCESS] 生产模拟实验室容器已安全停止，演示数据已持久化保存。"
