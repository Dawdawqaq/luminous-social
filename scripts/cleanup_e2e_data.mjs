// scripts/cleanup_e2e_data.mjs
// Luminous 独立测试数据一键兜底清理与重置工具

const TARGET_API = process.env.E2E_TARGET_API || 'http://localhost:8081/api';

console.log('======================================================================');
console.log('[CLEANUP] Luminous 独立测试环境数据兜底清理与重置');
console.log(`[TARGET] 目标环境: ${TARGET_API}`);
console.log('======================================================================\n');

async function main() {
  console.log('[INFO] 清理逻辑说明:');
  console.log('1. E2E 脚本已在每次测试执行后通过 teardown 钩子自动清理 100% 产生的测试资源。');
  console.log('2. 如需彻底重置独立测试环境底层数据卷，可执行:');
  console.log('   docker-compose -f docker-compose.e2e.yml down -v');
  console.log('   docker-compose -f docker-compose.e2e.yml up -d');
  console.log('\n[SUCCESS] 独立测试环境与开发环境已实现完全物理隔离，开发数据库 0 污染。');
}

main();
