#!/usr/bin/env bash
set -euo pipefail
script_dir=$(dirname -- "${BASH_SOURCE[0]}")
cd -- "$script_dir"

if [[ -e .env.cloud.local || -L .env.cloud.local ]]; then
  echo "云环境配置已存在，保留现有密钥。"
  exit 0
fi
command -v openssl >/dev/null || { echo "请先安装 openssl。"; exit 1; }
umask 077
db_password=$(openssl rand -hex 24)
db_root_password=$(openssl rand -hex 24)
redis_password=$(openssl rand -hex 24)
jwt_secret=$(openssl rand -hex 48)
# 禁止覆盖并发创建的环境文件，数据库卷初始化后不得自动轮换凭据。
set -o noclobber
cat > .env.cloud.local <<EOF
APP_PUBLIC_BASE_URL=https://myluminous.xyz
DB_PASSWORD=$db_password
DB_ROOT_PASSWORD=$db_root_password
REDIS_PASSWORD=$redis_password
JWT_SECRET=$jwt_secret
BOOTSTRAP_ADMIN_USERNAME=
BOOTSTRAP_ADMIN_PASSWORD=
EOF
echo "已生成 .env.cloud.local，权限仅允许当前用户读写。"
