#!/usr/bin/env bash
# scripts/reset-dev-env.sh
# Luminous Agent Dev/Test 环境秒级重置与重建脚本 (Linux/macOS)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR" || exit 1

echo "=========================================================="
echo "[RESET] Luminous Agent Dev/Test 环境秒级重置与纯净重建"
echo "=========================================================="

echo "1. 销毁旧 Dev 容器与数据卷..."
docker compose -f docker-compose.yml --env-file .env.dev down -v

echo "2. 启动全新 Dev/Test 基础设施与服务..."
docker compose -f docker-compose.yml --env-file .env.dev up -d --build

echo "3. 等待数据库与缓存就绪..."
bash "$SCRIPT_DIR/init-db.sh" ".env.dev"

echo "=========================================================="
echo "[SUCCESS] Dev/Test 纯净环境已就绪！"
echo "[URL] 前端访问地址: http://localhost:3000"
echo "[URL] 后端 API 地址: http://localhost:8080/api"
echo "=========================================================="
