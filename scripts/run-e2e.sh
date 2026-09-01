#!/usr/bin/env bash
# scripts/run-e2e.sh
# Luminous 自动化 E2E 测试启动与生命周期管理脚本 (Linux/macOS)

TARGET_API="${1:-http://localhost:8080/api}"

echo "=========================================================="
echo "[E2E_TEST] 执行 Luminous E2E 自动化端到端测试与生命周期闭环"
echo "[GATEWAY] 测试网关: $TARGET_API"
echo "=========================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

export E2E_TARGET_API="$TARGET_API"
node "$ROOT_DIR/scripts/run_e2e_isolated.mjs"

echo "[CLEANUP] 执行测试后兜底数据清理..."
bash "$SCRIPT_DIR/clean-test-data.sh"
