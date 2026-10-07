import { createTestFetch } from './test-api-auth.mjs';
/**
 * scripts/run_benchmark.mjs
 * Luminous 全场景生产级基准压力测试引擎
 */

const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api';
const testFetch = createTestFetch(BASE_URL);

function calculateStats(latencies) {
    if (latencies.length === 0) return { min: 0, max: 0, avg: 0, p50: 0, p90: 0, p95: 0, p99: 0 };
    latencies.sort((a, b) => a - b);
    const sum = latencies.reduce((acc, v) => acc + v, 0);
    const avg = (sum / latencies.length).toFixed(2);
    const p50 = latencies[Math.floor(latencies.length * 0.50)];
    const p90 = latencies[Math.floor(latencies.length * 0.90)];
    const p95 = latencies[Math.floor(latencies.length * 0.95)];
    const p99 = latencies[Math.floor(latencies.length * 0.99)];
    const min = latencies[0];
    const max = latencies[latencies.length - 1];
    return { min, max, avg, p50, p90, p95, p99, count: latencies.length };
}

async function runConcurrentBatch(taskFn, totalRequests, concurrency) {
    const latencies = [];
    let successCount = 0;
    let failCount = 0;
    let index = 0;

    const startTime = Date.now();

    async function worker() {
        while (true) {
            const currentIdx = index++;
            if (currentIdx >= totalRequests) break;
            const reqStart = Date.now();
            try {
                const res = await taskFn(currentIdx);
                const reqEnd = Date.now();
                latencies.push(reqEnd - reqStart);
                if (res) successCount++;
                else failCount++;
            } catch (err) {
                failCount++;
            }
        }
    }

    const workers = Array.from({ length: concurrency }, () => worker());
    await Promise.all(workers);

    const totalDuration = ((Date.now() - startTime) / 1000).toFixed(2);
    const rps = (totalRequests / totalDuration).toFixed(1);
    const stats = calculateStats(latencies);

    return { rps, totalDuration, successCount, failCount, stats };
}

async function main() {
    console.log('==========================================================');
    console.log('[BENCHMARK] Starting Luminous Full-Scenario Stress Test Suite');
    console.log(`[TARGET] Target API Base URL: ${BASE_URL}`);
    console.log('==========================================================\n');

    // 1. Setup Benchmark Seed Data
    console.log('>>> [PHASE 1] Initializing [BENCHMARK] Seed User and Post...');
    const seedUser = {
        username: 'E2E_TEST_author_' + Date.now().toString().slice(-4),
        displayName: '[E2E_TEST] 压测作者',
        password: 'Password123!'
    };
    
    // Register
    const regRes = await testFetch(`${BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true' },
        body: JSON.stringify(seedUser)
    });
    const regData = await regRes.json();
    const token = regData?.data?.token;

    // Create Community
    const commRes = await testFetch(`${BASE_URL}/communities`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({
            slug: 'e2e-bench-' + Date.now(),
            name: '[E2E_TEST] 压测社区',
            description: '[E2E_TEST] 压测社区'
        })
    });
    const commData = await commRes.json();
    const communitySlug = commData?.data?.name;

    // Create Benchmark Post
    const postRes = await testFetch(`${BASE_URL}/posts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({
            title: '[E2E_TEST] 高并发压测帖子',
            content: '[E2E_TEST] 检索与并发测试内容',
            community: communitySlug,
            isPublic: true
        })
    });
    const postData = await postRes.json();
    const postId = postData?.data?.id;
    console.log(`[OK] Seed Data Ready: Post ID = ${postId}, Community = ${communitySlug}\n`);

    // 2. Scenario 1A: Read-Heavy Post Feed Stream (1000 requests, concurrency 50)
    console.log('>>> [SCENARIO 1A] Feed Stream Listing Benchmark (GET /posts?filter=hot)...');
    const res1A = await runConcurrentBatch(async () => {
        const r = await testFetch(`${BASE_URL}/posts?filter=hot`);
        return r.status === 200;
    }, 1000, 50);
    console.log(`   [RESULT] RPS: ${res1A.rps} req/s | Total: 1000 reqs in ${res1A.totalDuration}s`);
    console.log(`   [LATENCY] Avg: ${res1A.stats.avg}ms | P50: ${res1A.stats.p50}ms | P95: ${res1A.stats.p95}ms | P99: ${res1A.stats.p99}ms | Max: ${res1A.stats.max}ms\n`);

    // 3. Scenario 1B: Post Detail + HyperLogLog UV Tracking (1000 requests, concurrency 50)
    console.log('>>> [SCENARIO 1B] Post Detail + HyperLogLog UV Tracking (GET /posts/:id)...');
    const res1B = await runConcurrentBatch(async () => {
        const r = await testFetch(`${BASE_URL}/posts/${postId}`);
        return r.status === 200;
    }, 1000, 50);
    console.log(`   [RESULT] RPS: ${res1B.rps} req/s | Total: 1000 reqs in ${res1B.totalDuration}s`);
    console.log(`   [LATENCY] Avg: ${res1B.stats.avg}ms | P50: ${res1B.stats.p50}ms | P95: ${res1B.stats.p95}ms | P99: ${res1B.stats.p99}ms | Max: ${res1B.stats.max}ms\n`);

    // 4. Scenario 2A: ngram FULLTEXT Search Benchmark (500 requests, concurrency 30)
    console.log('>>> [SCENARIO 2A] MySQL ngram FULLTEXT Search (GET /posts?search=Throughput)...');
    const res2A = await runConcurrentBatch(async () => {
        const r = await testFetch(`${BASE_URL}/posts?search=Throughput`);
        return r.status === 200;
    }, 500, 30);
    console.log(`   [RESULT] RPS: ${res2A.rps} req/s | Total: 500 reqs in ${res2A.totalDuration}s`);
    console.log(`   [LATENCY] Avg: ${res2A.stats.avg}ms | P50: ${res2A.stats.p50}ms | P95: ${res2A.stats.p95}ms | P99: ${res2A.stats.p99}ms | Max: ${res2A.stats.max}ms\n`);

    // 5. Scenario 2B: Search Circuit Breaker: Short Query < 2 chars (500 requests, concurrency 30)
    console.log('>>> [SCENARIO 2B] Search Short-Circuit Protection (GET /posts?search=a)...');
    const res2B = await runConcurrentBatch(async () => {
        const r = await testFetch(`${BASE_URL}/posts?search=a`);
        return r.status === 200;
    }, 500, 30);
    console.log(`   [RESULT] RPS: ${res2B.rps} req/s | Total: 500 reqs in ${res2B.totalDuration}s`);
    console.log(`   [LATENCY] Avg: ${res2B.stats.avg}ms | P50: ${res2B.stats.p50}ms | P95: ${res2B.stats.p95}ms | P99: ${res2B.stats.p99}ms | Max: ${res2B.stats.max}ms\n`);

    // 6. Scenario 2C: Deep Pagination Circuit Breaker (500 requests, concurrency 30)
    console.log('>>> [SCENARIO 2C] Deep Pagination Circuit Breaker (GET /posts?page=100&size=10)...');
    const res2C = await runConcurrentBatch(async () => {
        const r = await testFetch(`${BASE_URL}/posts?page=100&size=10`);
        return r.status === 200;
    }, 500, 30);
    console.log(`   [RESULT] RPS: ${res2C.rps} req/s | Total: 500 reqs in ${res2C.totalDuration}s`);
    console.log(`   [LATENCY] Avg: ${res2C.stats.avg}ms | P50: ${res2C.stats.p50}ms | P95: ${res2C.stats.p95}ms | P99: ${res2C.stats.p99}ms | Max: ${res2C.stats.max}ms\n`);

    // 7. Scenario 3: High-Concurrency Interactive Write Buffer (Vote POST /posts/:id/vote)
    console.log('>>> [SCENARIO 3] Extreme Write-Behind CQRS Vote Benchmark (POST /posts/:id/vote)...');
    // Pre-create 20 concurrent voters
    const voters = [];
    for (let i = 0; i < 20; i++) {
        const vUser = { username: `E2E_TEST_v_${Date.now()}_${i}`, displayName: `[E2E_TEST] 投票用户 ${i}`, password: 'Password123!' };
        const vRes = await testFetch(`${BASE_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true' },
            body: JSON.stringify(vUser)
        });
        const vData = await vRes.json();
        voters.push(vData?.data?.token);
    }
    
    // 400 concurrent votes from 20 voters (switching upvote/downvote)
    const res3 = await runConcurrentBatch(async (idx) => {
        const voterToken = voters[idx % voters.length];
        const voteValue = (idx % 2 === 0) ? 1 : -1;
        const r = await testFetch(`${BASE_URL}/posts/${postId}/vote`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${voterToken}` },
            body: JSON.stringify({ value: voteValue })
        });
        return r.status === 200;
    }, 400, 20);
    console.log(`   [RESULT] RPS: ${res3.rps} req/s | 成功: ${res3.successCount} / 400，失败: ${res3.failCount}。Total: 400 writes in ${res3.totalDuration}s`);
    console.log(`   [LATENCY] Avg: ${res3.stats.avg}ms | P50: ${res3.stats.p50}ms | P95: ${res3.stats.p95}ms | P99: ${res3.stats.p99}ms | Max: ${res3.stats.max}ms\n`);

    // 8. Scenario 4: Rate Limiter Burst Test (POST /auth/login without bypass)
    console.log('>>> [SCENARIO 4] Sliding Window Rate Limiter Burst Test (100 rapid logins)...');
    let okCount = 0;
    let rateLimitedCount = 0;
    for (let i = 0; i < 100; i++) {
        const r = await testFetch(`${BASE_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: seedUser.username, password: seedUser.password })
        });
        if (r.status === 200 || r.status === 400) okCount++;
        else if (r.status === 429) rateLimitedCount++;
    }
    console.log(`   [RESULT] 进入认证校验（200/400）: ${okCount} requests, 429 Rate Limited: ${rateLimitedCount} requests.`);
    console.log(`   [VERIFY] 限流是否达标以实际计数及请求耗时为准，缺少验证码的请求预期返回 400。\n`);

    console.log('==========================================================');
    console.log('[COMPLETE] 4 个场景执行完成，成功比例以各场景实际统计为准。');
    console.log('==========================================================');
}

main().catch(err => {
    console.error('[FAIL] Benchmark execution error:', err);
    process.exit(1);
});
