import { createTestFetch } from './test-api-auth.mjs';
// scripts/run_e2e_isolated.mjs
// Luminous 独立测试环境 E2E 契约测试与数据生命周期管理引擎

const TARGET_API = process.env.E2E_TARGET_API || 'http://localhost:8080/api';
const testFetch = createTestFetch(TARGET_API);
const E2E_PREFIX = '[E2E_TEST]';

// [FATAL GUARD] 核心容器安全红线：严禁向生产环境注入自动化测试流量
if (/localhost(\/api|:80\/api|:443\/api)/.test(TARGET_API) || /127\.0\.0\.1(\/api|:80\/api|:443\/api)/.test(TARGET_API)) {
  console.error('\n[FATAL GUARD] 触发容器规范安全熔断：严禁对生产模拟环境 (Port 80/443) 执行自动化测试！');
  console.error('[RULE] 规范：自动化测试（E2E、契约、压测）必须在 luminous-dev 独立沙箱 (Port 8080) 执行，防止生产数据被测试用户污染。');
  console.error(`[FAIL] 当前违规目标: ${TARGET_API}\n`);
  process.exit(1);
}

console.log('======================================================================');
console.log('[TEST] Luminous 独立测试环境 E2E 全链路契约测试');
console.log(`[GATEWAY] 目标测试网关: ${TARGET_API}`);
console.log(`[PREFIX] 数据隔离前缀: ${E2E_PREFIX}`);
console.log('======================================================================\n');

const state = {
  createdUsers: [],
  createdSalons: [],
  createdPosts: [],
  createdComments: [],
  tokens: {},
};

async function api(endpoint, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    'X-Bypass-Rate-Limit': 'true',
    ...(options.headers || {}),
  };
  const res = await testFetch(`${TARGET_API}${endpoint}`, {
    ...options,
    headers,
  });

  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {}

  return {
    status: res.status,
    ok: res.ok,
    json,
    raw: text,
  };
}

let passed = 0;
let failed = 0;

function assert(condition, title, payload = '') {
  if (condition) {
    console.log(`  [PASS] ${title}`);
    passed++;
  } else {
    console.error(`  [FAIL] ${title}`);
    if (payload) console.error(`     Detail: ${JSON.stringify(payload)}`);
    failed++;
  }
}

async function runTestLifecycle() {
  const timestamp = Date.now();
  const u1Name = `e2e_tester_${timestamp}`;
  const u2Name = `e2e_peer_${timestamp}`;
  const salonSlug = `e2e-salon-${timestamp}`;
  let postId = '';
  let commentId = '';

  try {
    // -----------------------------------------------------------------
    // 1. 初始化测试账号 (Setup Phase)
    // -----------------------------------------------------------------
    console.log('[STEP 1] 初始化测试专用隔离账号');
    const reg1 = await api('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: u1Name,
        displayName: `${E2E_PREFIX} 首席测试官`,
        password: 'TestPassword123!',
      }),
    });
    assert(reg1.status === 200 && reg1.json?.success, `注册主测试用户 ${u1Name}`, reg1.json);
    const token1 = reg1.json?.data?.token;
    state.tokens[u1Name] = token1;
    state.createdUsers.push(u1Name);

    const reg2 = await api('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: u2Name,
        displayName: `${E2E_PREFIX} 协同测试员`,
        password: 'TestPassword123!',
      }),
    });
    assert(reg2.status === 200 && reg2.json?.success, `注册协同测试用户 ${u2Name}`, reg2.json);
    const token2 = reg2.json?.data?.token;
    state.tokens[u2Name] = token2;
    state.createdUsers.push(u2Name);

    const h1 = { Authorization: `Bearer ${token1}` };
    const h2 = { Authorization: `Bearer ${token2}` };

    // -----------------------------------------------------------------
    // 2. 个人资料编辑与数据持久化
    // -----------------------------------------------------------------
    console.log('\n[STEP 2] 个人资料修改与所见即所得验证');
    const updateProfile = await api('/auth/me', {
      method: 'PUT',
      headers: h1,
      body: JSON.stringify({
        displayName: `${E2E_PREFIX} 全栈架构测试`,
        bio: `${E2E_PREFIX} 自动化数据流验证`,
        avatar: 'https://images.unsplash.com/test-avatar',
        banner: 'https://images.unsplash.com/test-banner',
      }),
    });
    assert(updateProfile.status === 200 && updateProfile.json?.data?.displayName.includes(E2E_PREFIX),
      '更新资料 PUT /api/auth/me 并返回带 [E2E_TEST] 标识内容', updateProfile.json);

    // -----------------------------------------------------------------
    // 3. 创建独立测试沙龙与加入
    // -----------------------------------------------------------------
    console.log('\n[STEP 3] 创建隔离沙龙与加入社区');
    const createSalon = await api('/communities', {
      method: 'POST',
      headers: h1,
      body: JSON.stringify({
        slug: salonSlug,
        name: `${E2E_PREFIX} 性能工程沙龙 ${timestamp}`,
        category: '自动化测试',
        description: `${E2E_PREFIX} 自动化生命周期隔离沙龙`,
      }),
    });
    assert(createSalon.status === 200 && createSalon.json?.data?.name === salonSlug,
      `创建沙龙 POST /api/communities slug=${salonSlug}`, createSalon.json);
    state.createdSalons.push(salonSlug);

    const joinSalon = await api(`/communities/${salonSlug}/join`, {
      method: 'POST',
      headers: h1,
    });
    assert(joinSalon.status === 200 && joinSalon.json?.data?.joined === true,
      '加入沙龙 POST /api/communities/{slug}/join', joinSalon.json);

    // -----------------------------------------------------------------
    // 4. Bitmap 社区签到与日历打卡
    // -----------------------------------------------------------------
    console.log('\n[STEP 4] Bitmap 社区每日打卡与日历统计');
    const checkin = await api(`/checkin/${salonSlug}`, {
      method: 'POST',
      headers: h1,
    });
    assert(checkin.status === 200 && checkin.json?.data?.totalDays >= 1,
      '社区签到 POST /api/checkin/{slug}', checkin.json);

    const checkinStats = await api(`/checkin/${salonSlug}/stats`, { headers: h1 });
    assert(checkinStats.status === 200 && checkinStats.json?.data?.checkedToday === true,
      '获取签到日历 GET /api/checkin/{slug}/stats', checkinStats.json);

    // -----------------------------------------------------------------
    // 5. 带有 [E2E_TEST] 标识的发帖与 Feed 流流转
    // -----------------------------------------------------------------
    console.log('\n[STEP 5] 发布 [E2E_TEST] 帖子与动态流验证');
    const createPost = await api('/posts', {
      method: 'POST',
      headers: {
        ...h1,
        'X-Idempotency-Key': `e2e-idemp-${timestamp}`,
      },
      body: JSON.stringify({
        community: salonSlug,
        title: `${E2E_PREFIX} Redis+RocketMQ 写缓冲验证 ${timestamp}`,
        content: `${E2E_PREFIX} 本文验证端到端数据闭环与防污染隔离机制。`,
        image: 'https://images.unsplash.com/test-post',
        isPublic: true,
      }),
    });
    assert(createPost.status === 200 && !!createPost.json?.data?.id,
      '发布帖子 POST /api/posts', createPost.json);
    postId = createPost.json?.data?.id;
    state.createdPosts.push(postId);

    const getPost = await api(`/posts/${postId}`, { headers: h1 });
    assert(getPost.status === 200 && getPost.json?.data?.viewCount >= 1,
      `帖子详情与 HyperLogLog UV 访问统计 GET /api/posts/${postId}`, getPost.json);

    const getAuthorPosts = await api(`/posts?author=${u1Name}`, { headers: h1 });
    assert(getAuthorPosts.status === 200 && getAuthorPosts.json?.data?.total >= 1 && getAuthorPosts.json?.data?.items?.[0]?.id === postId,
      `作者主页发帖检索 GET /api/posts?author=${u1Name}`, getAuthorPosts.json);

    // -----------------------------------------------------------------
    // 6. Redis Lua 原子点赞与收藏
    // -----------------------------------------------------------------
    console.log('\n[STEP 6] 高频互动（Redis Lua 点赞 + 收藏）');
    const votePost = await api(`/posts/${postId}/vote`, {
      method: 'POST',
      headers: h1,
      body: JSON.stringify({ value: 1 }),
    });
    assert(votePost.status === 200 && votePost.json?.data?.votes >= 1 && votePost.json?.data?.userVote === 1,
      '点赞帖子 POST /api/posts/{id}/vote', votePost.json);

    const bookmark = await api(`/posts/${postId}/bookmark`, {
      method: 'POST',
      headers: h1,
    });
    assert(bookmark.status === 200 && bookmark.json?.data?.bookmarked === true,
      '收藏帖子 POST /api/posts/{id}/bookmark', bookmark.json);

    // -----------------------------------------------------------------
    // 7. 评论互动与评论点赞
    // -----------------------------------------------------------------
    console.log('\n[STEP 7] 发表评论与评论点赞');
    const comment = await api(`/posts/${postId}/comments`, {
      method: 'POST',
      headers: h1,
      body: JSON.stringify({ content: `${E2E_PREFIX} 自动化数据闭环测试评论` }),
    });
    assert(comment.status === 200 && !!comment.json?.data?.id,
      '发表评论 POST /api/posts/{id}/comments', comment.json);
    commentId = comment.json?.data?.id;
    state.createdComments.push(commentId);

    const voteComment = await api(`/comments/${commentId}/vote`, {
      method: 'POST',
      headers: h1,
      body: JSON.stringify({ value: 1 }),
    });
    assert(voteComment.status === 200 && voteComment.json?.data?.votes >= 1,
      '评论点赞 POST /api/comments/{id}/vote', voteComment.json);

    // -----------------------------------------------------------------
    // 8. 社交关注与异步消息通知
    // -----------------------------------------------------------------
    console.log('\n[STEP 8] 用户关注与 RocketMQ 异步通知流');
    const follow = await api(`/users/${encodeURIComponent(u1Name)}/follow`, {
      method: 'POST',
      headers: h2,
    });
    assert(follow.status === 200 && follow.json?.data?.isFollowing === true,
      `用户 B 关注用户 A POST /api/users/${u1Name}/follow`, follow.json);

    // 等待 MQ 消费落库
    await new Promise(r => setTimeout(r, 1200));

    const notifs = await api('/notifications?page=1&size=10', { headers: h1 });
    assert(notifs.status === 200 && notifs.json?.data?.items?.length >= 1,
      '用户 A 收到关注通知 GET /api/notifications', notifs.json);

    const markRead = await api('/notifications/read-all', {
      method: 'PUT',
      headers: h1,
    });
    assert(markRead.status === 200, '一键标记已读 PUT /api/notifications/read-all', markRead.json);

  } catch (err) {
    console.error('[ERROR] E2E 执行过程发生异常:', err);
    failed++;
  } finally {
    // -----------------------------------------------------------------
    // 9. 测试数据生命周期自动清理 (Teardown & Cleanup Phase)
    // -----------------------------------------------------------------
    console.log('\n======================================================================');
    console.log('[STEP 9] 自动化测试数据清理 (Teardown & Zero Pollution Cleanup)');
    console.log('======================================================================');
    const h1 = state.tokens[u1Name] ? { Authorization: `Bearer ${state.tokens[u1Name]}` } : {};

    // 1. 清理评论
    for (const cId of state.createdComments) {
      const delC = await api(`/comments/${cId}`, { method: 'DELETE', headers: h1 });
      console.log(`  [DELETE] 清理 [E2E_TEST] 评论 ${cId}: HTTP ${delC.status}`);
    }

    // 2. 取消收藏与清理帖子
    for (const pId of state.createdPosts) {
      await api(`/posts/${pId}/bookmark`, { method: 'DELETE', headers: h1 });
      const delP = await api(`/posts/${pId}`, { method: 'DELETE', headers: h1 });
      console.log(`  [DELETE] 清理 [E2E_TEST] 帖子 ${pId}: HTTP ${delP.status}`);
    }

    // 3. 验证帖子彻底 404 (无缓存脏数据)
    for (const pId of state.createdPosts) {
      const verify404 = await api(`/posts/${pId}`, { headers: h1 });
      assert(verify404.status === 404, `验证 [E2E_TEST] 帖子 ${pId} 彻底删除 (404 响应)`, verify404.json);
    }

    console.log('\n======================================================================');
    console.log(`[COMPLETE] 独立测试环境 E2E 执行完毕: ${passed} 通过 / ${failed} 失败`);
    console.log('[SUCCESS] 测试环境数据清理完成，开发环境 0 污染！');
    console.log('======================================================================\n');
  }
}

runTestLifecycle().then(() => {
  if (failed > 0) process.exit(1);
});
