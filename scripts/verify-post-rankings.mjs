import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { createTestFetch, requireDevTarget } from './test-api-auth.mjs';

const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || '', /^\[E2E_TEST\] rankings-\d+$/);
const stamp = marker.split('rankings-')[1], base = 'http://localhost:8080/api', origin = 'http://localhost:3000';
requireDevTarget(base);
const request = createTestFetch(base), checks = [], faults = [], output = 'output/playwright/post-rankings';
const docker = 'C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
await mkdir(output, { recursive: true });
for (let attempt = 0; attempt < 60; attempt++) {
  try { if ((await fetch(base + '/users/profile-presets')).ok) break; } catch {}
  if (attempt === 59) throw new Error('开发服务启动超时');
  await new Promise(resolve => setTimeout(resolve, 500));
}
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function sql(text) { return execFileSync(docker, ['exec', '-i', 'luminous-dev-mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'], { input: text, encoding: 'utf8' }).trim(); }
function redis(...args) { return execFileSync(docker, ['exec', 'luminous-dev-redis', 'redis-cli', '--raw', ...args], { encoding: 'utf8' }).trim(); }
function pass(text) { checks.push(text); console.log('[PASS] ' + text); }
async function api(path, method = 'GET', body, token) {
  const response = await request(base + path, { method, headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true', 'X-Idempotency-Key': randomUUID(), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  return { status: response.status, ...(response.status === 204 ? { success: true, data: null } : await response.json()) };
}
async function ok(path, method, body, token) { const result = await api(path, method, body, token); assert.ok([200, 204].includes(result.status), path + ': ' + JSON.stringify(result.error)); return result.data; }
async function until(load, predicate) {
  const deadline = Date.now() + 25000;
  let value;
  do { value = await load(); if (predicate(value)) return value; await sleep(150); } while (Date.now() < deadline);
  assert.fail('等待异步投影超时：' + JSON.stringify(value));
}
async function account(label) { return ok('/auth/register', 'POST', { username: `E2E_TEST_r${label}_${stamp.slice(-9)}`, displayName: `${marker} ${label}`, password: 'Password123!' }); }
const actors = await Promise.all(['owner', 'a', 'b', 'voter'].map(account));
const [owner, a, b, voter] = actors;
const community = await ok('/communities', 'POST', { slug: `e2e-rank-${stamp}`, name: marker, description: marker, category: 'Living' }, owner.token);
const empty = await ok('/communities', 'POST', { slug: `e2e-rank-empty-${stamp}`, name: marker + ' empty', description: marker, category: 'Living' }, owner.token);
assert.equal(sql(`SELECT LOCATE('${marker}',name)>0 FROM communities WHERE id=${community.id};`), '1');
for (const actor of [a, b]) await ok(`/communities/${community.name}/join`, 'POST', null, actor.token);
// 排序夹具需要密集建立样本；只调整本批标记账号的成功额度，不改变服务端限流配置。
function prepareFixturePost(actor) {
  assert.ok(actor.user.displayName.startsWith(marker));
  sql(`DELETE FROM post_publications WHERE user_id=${actor.user.id}; UPDATE post_publish_gates SET last_published_at=NULL WHERE user_id=${actor.user.id};`);
}
const candidateKey = `luminous:post-ranking:v1:candidates:${community.id}`;
const posts = [], byId = new Map();
for (let index = 0; index < 27; index++) {
  const actor = actors[index % 3];
  prepareFixturePost(actor);
  const post = await ok('/posts', 'POST', { community: community.name, title: `[E2E_TEST] R${String(index).padStart(2, '0')}`, content: marker + ' 推荐和榜单验收', isPublic: true }, actor.token);
  posts.push(post); byId.set(post.id, { post, actor });
  if (index !== 0) await ok(`/posts/${post.id}/vote`, 'POST', { value: 1 }, voter.token);
}
const special = posts[0];
sql(`UPDATE posts SET created_at=NOW()-INTERVAL 2 DAY WHERE id=${posts[24].id}; UPDATE posts SET created_at=NOW()-INTERVAL 8 DAY WHERE id=${posts[25].id}; UPDATE posts SET is_public=FALSE WHERE id=${posts[26].id};`);
function invalidate() { redis('DEL', candidateKey); }
async function ranking(type = 'hot', period = 'day', cursor, size = 30, actor = owner) {
  return ok(`/posts/ranking?type=${type}&period=${period}&community=${community.name}&size=${size}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`, 'GET', null, actor?.token);
}
function metric(id) {
  // Redis 内部使用 Java Long；测试脚本解析时转成字符串，避免 JavaScript 丢失雪花编号精度。
  const raw = redis('GET', candidateKey).replace(/("id"\s*:\s*)(\d+)/g, '$1"$2"');
  return JSON.parse(raw).candidates.find(post => String(post.id) === id);
}
invalidate();
let day = await ranking(), week = await ranking('hot', 'week');
assert.ok(!day.items.some(post => post.id === posts[24].id));
assert.ok(week.items.some(post => post.id === posts[24].id));
assert.ok(!week.items.some(post => [posts[25].id, posts[26].id].includes(post.id)));
assert.ok(!day.items.some(post => post.id === special.id));
pass('日榜/周榜按发布时间分开，8天前及非公开帖子排除；无外部互动不进入热榜');

await ok(`/posts/${special.id}/vote`, 'POST', { value: 1 }, owner.token);
invalidate(); await ranking(); assert.equal(metric(special.id).netVotes, 0);
for (const [value, expected] of [[1, 1], [1, 1], [0, 0], [-1, -1], [1, 1]]) {
  await ok(`/posts/${special.id}/vote`, 'POST', { value }, a.token);
  invalidate(); await ranking(); assert.equal(metric(special.id).netVotes, expected);
  await until(() => Promise.resolve(sql(`SELECT p.votes=c.accepted_total AND p.votes=COALESCE((SELECT SUM(value) FROM post_votes WHERE post_id=p.id),0) FROM posts p JOIN post_vote_clocks c ON c.post_id=p.id WHERE p.id=${special.id};`)), value => value === '1');
  assert.equal(Number(redis('GET', `luminous:stat:post:votes:${special.id}`)), expected + 1);
}
pass('自己的赞不加热度；首次、重复、取消、赞踩切换的有效分值正确，Redis/投票记录/帖子分值最终一致');

const seed = 7200000000000000000n + BigInt(stamp.slice(-9)) * 10n;
sql(`INSERT INTO comments(id,post_id,author_id,content,votes) VALUES(${seed},${special.id},${a.user.id},'${marker} first',0),(${seed + 1n},${special.id},${a.user.id},'${marker} repeat',0),(${seed + 2n},${special.id},${owner.user.id},'${marker} own',0); UPDATE posts SET comments_count=3 WHERE id=${special.id};`);
invalidate(); await ranking(); assert.equal(metric(special.id).participants, 1);
await ok(`/comments/${seed}`, 'DELETE', null, a.token);
invalidate(); await ranking(); assert.equal(metric(special.id).participants, 1);
await ok(`/comments/${seed + 1n}`, 'DELETE', null, a.token);
invalidate(); await ranking(); assert.equal(metric(special.id).participants, 0);
pass('同一人多次评论只计一次，作者评论不计，最后一条评论删除后讨论贡献扣回');

const recommendation = await ranking('recommend');
assert.equal(new Set(recommendation.items.map(post => post.id)).size, recommendation.items.length);
for (let index = 1; index < recommendation.items.length; index++) {
  if (recommendation.items[index].author.username === recommendation.items[index - 1].author.username)
    assert.equal(new Set(recommendation.items.slice(index).map(post => post.author.username)).size, 1, '只有其他作者耗尽后才允许连续');
}
pass('真实推荐输出去重并打散作者，与投票热榜走独立排序');

invalidate();
const baseline = await ranking(), first = await ranking('hot', 'day', null, 5);
const hiddenIds = baseline.items.slice(5, 8).map(post => post.id);
assert.equal(hiddenIds.length, 3);
sql(`UPDATE posts SET status='BANNED' WHERE id=${hiddenIds[0]}; UPDATE posts SET is_public=FALSE WHERE id=${hiddenIds[1]};`);
await ok(`/posts/${hiddenIds[2]}`, 'DELETE', null, byId.get(hiddenIds[2]).actor.token);
prepareFixturePost(owner);
const fresh = await ok('/posts', 'POST', { community: community.name, title: '[E2E_TEST] NEW', content: marker, isPublic: true }, owner.token);
await ok(`/posts/${fresh.id}/vote`, 'POST', { value: 1 }, voter.token);
const promoted = baseline.items.at(-1);
for (const actor of [a, b]) if (actor.user.username !== promoted.author.username) await ok(`/posts/${promoted.id}/vote`, 'POST', { value: 1 }, actor.token);
invalidate();
assert.ok((await ranking()).items.some(post => post.id === fresh.id));
const read = [...first.items]; let cursor = first.nextCursor;
while (cursor) { const page = await ranking('hot', 'day', cursor, 5); read.push(...page.items); cursor = page.nextCursor; }
assert.deepEqual(read.map(post => post.id), baseline.items.filter(post => !hiddenIds.includes(post.id)).map(post => post.id));
assert.ok(!read.some(post => post.id === fresh.id));
pass('投票变化及新帖不扰动已有分页；缓存中的封禁、转非公开和删除项即时跳过，无重复或漏掉可见旧项');

const expiring = await ranking('hot', 'day', null, 5);
redis('DEL', `luminous:post-ranking:v1:snapshot:${expiring.nextCursor.split('.')[0]}`);
assert.equal((await api(`/posts/ranking?type=hot&period=day&community=${community.name}&cursor=${expiring.nextCursor}`, 'GET', null, owner.token)).status, 410);
assert.equal((await api('/posts/ranking?type=other')).status, 400);
assert.equal((await api('/posts/ranking?type=hot&size=0')).status, 400);
const scoped = await ranking('hot', 'day', null, 5);
assert.equal((await api(`/posts/ranking?type=hot&period=week&community=${community.name}&cursor=${scoped.nextCursor}`, 'GET', null, owner.token)).status, 400);
pass('快照过期返回410，非法参数和跨榜单游标被拒绝');

const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
async function focusFeed(page, feed) {
  await page.evaluate(() => document.fonts.ready);
  await feed.evaluate(element => {
    const headerHeight = document.querySelector('.site-header')?.getBoundingClientRect().height || 0;
    window.scrollTo({ top: Math.max(0, window.scrollY + element.getBoundingClientRect().top - headerHeight - 12), behavior: 'instant' });
  });
}
try {
  for (const [device, width, height] of [['desktop', 1440, 1000], ['mobile', 390, 844]]) {
    const context = await browser.newContext({ viewport: { width, height }, reducedMotion: 'reduce' });
    await context.addInitScript(token => localStorage.setItem('luminous_token', token), owner.token);
    const page = await context.newPage(); page.setDefaultTimeout(15000);
    page.on('pageerror', error => faults.push(error.message));
    await page.goto(`${origin}/?filter=hot&community=${community.name}`);
    const feed = page.getByRole('region', { name: '帖子列表', exact: true });
    await feed.locator('article').first().waitFor();
    assert.equal(await feed.locator('article').count(), 10);
    await focusFeed(page, feed);
    await page.screenshot({ path: `${output}/recommend-${device}.png` });
    const hotResponse = page.waitForResponse(response => response.url().includes('/posts/ranking?') && response.url().includes('type=hot'));
    await feed.getByRole('tab', { name: '热榜', exact: true }).click();
    await hotResponse;
    await feed.getByRole('group', { name: '热榜时间范围' }).waitFor();
    await feed.locator('article').first().waitFor();
    await focusFeed(page, feed);
    await page.screenshot({ path: `${output}/hot-${device}.png` });
    const weekResponse = page.waitForResponse(response => response.url().includes('/posts/ranking?') && response.url().includes('period=week'));
    await feed.getByRole('button', { name: '周榜', exact: true }).click();
    const weekData = (await (await weekResponse).json()).data;
    assert.ok(weekData.items.some(post => post.id === posts[24].id) || weekData.hasMore);
    await feed.getByRole('button', { name: '加载更多', exact: true }).click();
    await until(() => feed.locator('article').count(), count => count > 10);
    const titles = await feed.locator('article h2').allTextContents();
    assert.equal(new Set(titles).size, titles.length);
    await feed.getByRole('button', { name: '刷新热榜', exact: true }).click();
    await until(() => feed.locator('article').count(), count => count === 10);
    const initial = page.waitForResponse(response => response.url().includes('/posts/ranking?') && !response.url().includes('cursor='));
    await feed.getByRole('button', { name: '刷新热榜', exact: true }).click();
    const snapshot = (await (await initial).json()).data.nextCursor.split('.')[0];
    redis('DEL', `luminous:post-ranking:v1:snapshot:${snapshot}`);
    await feed.getByRole('button', { name: '加载更多', exact: true }).click();
    await feed.getByRole('button', { name: '刷新列表', exact: true }).waitFor();
    await feed.getByRole('button', { name: '刷新列表', exact: true }).click();
    await until(() => feed.locator('article').count(), count => count === 10);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.evaluate(() => document.documentElement.classList.add('dark'));
    await focusFeed(page, feed);
    await page.screenshot({ path: `${output}/dark-${device}.png` });
    await page.goto(`${origin}/?filter=top&community=${empty.name}`);
    await feed.getByRole('heading', { name: '暂时没有上榜帖子', exact: true }).waitFor();
    await focusFeed(page, feed);
    await page.screenshot({ path: `${output}/empty-${device}.png` });
    await feed.getByRole('button', { name: '看看最新', exact: true }).click();
    assert.ok(page.url().includes('filter=new'));
    pass(`${device}：推荐十条、日周切换、追加去重、刷新、过期恢复、空榜及深色布局正常，无横向溢出`);
    await context.close();
  }
  assert.deepEqual(faults, []);
  await writeFile(`${output}/result.json`, JSON.stringify({ marker, checks, faults }, null, 2));
} finally { await browser.close(); }
