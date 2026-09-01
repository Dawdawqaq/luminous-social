// scripts/benchmark_suite.mjs
// Luminous Full-Scope Benchmark & Stress Testing Suite (Targets luminous-dev :8080)

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080/api';

// Guard: Strict environment isolation check
if (BASE_URL.includes(':80/') || BASE_URL.includes(':8088/') || BASE_URL.endsWith(':80') || BASE_URL.endsWith(':8088')) {
    console.error('[FATAL GUARD] Benchmark suite MUST NOT run against production simulation lab! Target port is disallowed.');
    process.exit(1);
}

function calculatePercentiles(latencies) {
    if (latencies.length === 0) return { p50: 0, p90: 0, p95: 0, p99: 0, avg: 0, min: 0, max: 0 };
    latencies.sort((a, b) => a - b);
    const p50 = latencies[Math.floor(latencies.length * 0.50)];
    const p90 = latencies[Math.floor(latencies.length * 0.90)];
    const p95 = latencies[Math.floor(latencies.length * 0.95)];
    const p99 = latencies[Math.floor(latencies.length * 0.99)];
    const min = latencies[0];
    const max = latencies[latencies.length - 1];
    const avg = (latencies.reduce((sum, v) => sum + v, 0) / latencies.length).toFixed(2);
    return { p50, p90, p95, p99, avg, min, max };
}

async function request(url, options = {}) {
    const start = performance.now();
    try {
        const res = await fetch(url, options);
        const duration = performance.now() - start;
        let body = null;
        try {
            body = await res.json();
        } catch {
            // non-json response
        }
        return { status: res.status, duration, body, ok: res.ok };
    } catch (err) {
        const duration = performance.now() - start;
        return { status: 0, duration, error: err.message, ok: false };
    }
}

async function runConcurrentBatch(taskFn, totalRequests, concurrency) {
    let completed = 0;
    const latencies = [];
    const statusCounts = {};
    const startTime = performance.now();

    async function worker() {
        while (true) {
            const currentIdx = completed++;
            if (currentIdx >= totalRequests) break;
            const res = await taskFn(currentIdx);
            latencies.push(res.duration);
            statusCounts[res.status] = (statusCounts[res.status] || 0) + 1;
        }
    }

    const workers = Array.from({ length: concurrency }, () => worker());
    await Promise.all(workers);
    const totalDuration = (performance.now() - startTime) / 1000;
    const rps = (totalRequests / totalDuration).toFixed(2);
    const stats = calculatePercentiles(latencies);

    return { totalRequests, totalDuration, rps, stats, statusCounts };
}

async function main() {
    console.log('===============================================================');
    console.log('  Luminous 性能压测套件 (Full-Scope Benchmark Suite)');
    console.log(`  Target Base URL: ${BASE_URL}`);
    console.log('===============================================================\n');

    // 1. Setup Benchmark Data
    console.log('[STEP 1/5] Initializing [BENCHMARK] seed users and posts...');
    
    // Register Author
    const authorRes = await request(`${BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true' },
        body: JSON.stringify({ username: 'bench_author', displayName: 'Bench Author', password: 'password123' })
    });
    const authorToken = authorRes.body?.data?.token;
    let token = authorToken;
    if (!token) {
        // Try login if already exists
        const loginRes = await request(`${BASE_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true' },
            body: JSON.stringify({ username: 'bench_author', password: 'password123' })
        });
        token = loginRes.body?.data?.token;
    }

    if (!token) {
        console.error('[ERROR] Failed to authenticate benchmark author user.');
        process.exit(1);
    }

    // Create benchmark community
    await request(`${BASE_URL}/communities`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ slug: 'benchmark-lab', name: 'Benchmark Lab', description: '[BENCHMARK] Stress Testing Community' })
    });

    // Create target benchmark post
    const postRes = await request(`${BASE_URL}/posts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({
            title: '[BENCHMARK] High-Concurrency Post for Stress Testing and ngram FullText Search',
            content: 'Luminous platform high concurrency test content with keywords: spring-boot, mysql, redis, rocketmq and distributed lock evaluation.',
            communityId: 'benchmark-lab',
            type: 'standard'
        })
    });
    const targetPostId = postRes.body?.data?.id;
    console.log(`[OK] Target post created with ID: ${targetPostId}\n`);

    // Register a batch of 50 benchmark voters for write-heavy testing
    console.log('[STEP 2/5] Registering 50 benchmark voter accounts...');
    const voterTokens = [];
    for (let i = 1; i <= 50; i++) {
        const reg = await request(`${BASE_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true' },
            body: JSON.stringify({ username: `bench_voter_${i}`, displayName: `Voter ${i}`, password: 'password123' })
        });
        let vToken = reg.body?.data?.token;
        if (!vToken) {
            const login = await request(`${BASE_URL}/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true' },
                body: JSON.stringify({ username: `bench_voter_${i}`, password: 'password123' })
            });
            vToken = login.body?.data?.token;
        }
        if (vToken) voterTokens.push(vToken);
    }
    console.log(`[OK] ${voterTokens.length} voter tokens ready.\n`);

    // -------------------------------------------------------------
    // SCENARIO 1: High-Concurrency Hot Post Voting (Write Buffer CQRS)
    // -------------------------------------------------------------
    console.log('---------------------------------------------------------------');
    console.log('  [SCENARIO 1] 高并发热点帖子并发写/投票压测 (Redis Lua + RocketMQ)');
    console.log('  Testing 500 concurrent vote requests to the same post (50 concurrency)...');
    console.log('---------------------------------------------------------------');

    const writeBench = await runConcurrentBatch((idx) => {
        const vToken = voterTokens[idx % voterTokens.length];
        const voteVal = (idx % 2 === 0) ? 1 : -1;
        return request(`${BASE_URL}/posts/${targetPostId}/vote`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${vToken}` },
            body: JSON.stringify({ value: voteVal })
        });
    }, 500, 50);

    console.log(`  Requests: ${writeBench.totalRequests} | Duration: ${writeBench.totalDuration.toFixed(2)}s | RPS: ${writeBench.rps}`);
    console.log(`  Latencies: P50: ${writeBench.stats.p50.toFixed(1)}ms | P90: ${writeBench.stats.p90.toFixed(1)}ms | P99: ${writeBench.stats.p99.toFixed(1)}ms | Avg: ${writeBench.stats.avg}ms`);
    console.log(`  Status Distribution: ${JSON.stringify(writeBench.statusCounts)}\n`);

    // -------------------------------------------------------------
    // SCENARIO 2: Read-Heavy Post List & UV (HyperLogLog & Cache)
    // -------------------------------------------------------------
    console.log('---------------------------------------------------------------');
    console.log('  [SCENARIO 2] 读密集 Feed 流与帖子详情压测 (HyperLogLog UV + Cache)');
    console.log('  Testing 1,000 requests across list & detail endpoints (50 concurrency)...');
    console.log('---------------------------------------------------------------');

    const readBench = await runConcurrentBatch((idx) => {
        if (idx % 2 === 0) {
            return request(`${BASE_URL}/posts?filter=hot&page=1&size=10`);
        } else {
            return request(`${BASE_URL}/posts/${targetPostId}`);
        }
    }, 1000, 50);

    console.log(`  Requests: ${readBench.totalRequests} | Duration: ${readBench.totalDuration.toFixed(2)}s | QPS: ${readBench.rps}`);
    console.log(`  Latencies: P50: ${readBench.stats.p50.toFixed(1)}ms | P90: ${readBench.stats.p90.toFixed(1)}ms | P99: ${readBench.stats.p99.toFixed(1)}ms | Avg: ${readBench.stats.avg}ms`);
    console.log(`  Status Distribution: ${JSON.stringify(readBench.statusCounts)}\n`);

    // -------------------------------------------------------------
    // SCENARIO 3: ngram FULLTEXT Search & Deep Pagination Breaker
    // -------------------------------------------------------------
    console.log('---------------------------------------------------------------');
    console.log('  [SCENARIO 3] MySQL ngram 全文检索与深分页熔断防穿透压测');
    console.log('  Testing 1,000 mixed queries: fulltext keyword, short search & deep page...');
    console.log('---------------------------------------------------------------');

    const searchBench = await runConcurrentBatch((idx) => {
        const mode = idx % 3;
        if (mode === 0) {
            // Valid ngram FULLTEXT search
            return request(`${BASE_URL}/posts?search=luminous&page=1&size=10`);
        } else if (mode === 1) {
            // Short search guard (< 2 chars)
            return request(`${BASE_URL}/posts?search=a`);
        } else {
            // Deep pagination circuit breaker (> 500)
            return request(`${BASE_URL}/posts?page=100&size=10`);
        }
    }, 1000, 50);

    console.log(`  Requests: ${searchBench.totalRequests} | Duration: ${searchBench.totalDuration.toFixed(2)}s | QPS: ${searchBench.rps}`);
    console.log(`  Latencies: P50: ${searchBench.stats.p50.toFixed(1)}ms | P90: ${searchBench.stats.p90.toFixed(1)}ms | P99: ${searchBench.stats.p99.toFixed(1)}ms | Avg: ${searchBench.stats.avg}ms`);
    console.log(`  Status Distribution: ${JSON.stringify(searchBench.statusCounts)}\n`);

    // -------------------------------------------------------------
    // SCENARIO 4: Rate Limiter & Auth Resilience Check
    // -------------------------------------------------------------
    console.log('---------------------------------------------------------------');
    console.log('  [SCENARIO 4] 滑动窗口限流器 (SlidingWindowRateLimiter) 突发防刷压测');
    console.log('  Testing rapid 100 authentication attempts from single IP (no bypass)...');
    console.log('---------------------------------------------------------------');

    let passCount = 0;
    let rateLimitedCount = 0;
    for (let i = 0; i < 100; i++) {
        const res = await request(`${BASE_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: 'bench_author', password: 'password123' })
        });
        if (res.status === 200) passCount++;
        if (res.status === 429) rateLimitedCount++;
    }
    console.log(`  Allowed Requests: ${passCount} | Rate-Limited (429): ${rateLimitedCount}`);
    console.log(`  Rate Limiter Protection Status: ${rateLimitedCount > 0 ? '[PASS] 429 Circuit-Breaker Triggered' : '[WARN] Rate limit not exceeded'}\n`);

    console.log('===============================================================');
    console.log('  [COMPLETE] 全链路性能压测完成！');
    console.log('===============================================================');
}

main().catch(err => {
    console.error('[ERROR] Benchmark execution failed:', err);
    process.exit(1);
});
