#!/usr/bin/env bash
# scripts/start-prod-lab.sh
# Luminous 生产模拟实验室启动脚本 (Linux/macOS)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR" || exit 1

echo "=========================================================="
echo "[PROD-LAB] 启动 Luminous 生产模拟实验室 (Production Simulation Lab)"
echo "=========================================================="

if command -v lsof >/dev/null 2>&1; then
    if lsof -i :80 -sTCP:LISTEN >/dev/null 2>&1; then
        echo "[WARN] 检测到宿主机 80 端口已被监听，若拉起失败可在 .env.prod 中设置 PROD_HTTP_PORT=8088"
    fi
fi

docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build

echo "=========================================================="
echo "[SUCCESS] 生产模拟实验室已成功拉起！"
echo "[GATEWAY] 统一入口 (Nginx 负载均衡): http://localhost"
echo "[GATEWAY] API 集群网关: http://localhost/api/"
echo "[TOPOLOGY] 拓扑架构: Nginx:80 -> backend-1 & backend-2 (轮询负载均衡) -> MySQL / Redis / RocketMQ"
echo "=========================================================="
