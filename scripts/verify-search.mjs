import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { performance } from 'node:perf_hooks';
import { createTestFetch, requireDevTarget } from './test-api-auth.mjs';
const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || '', /^\[E2E_TEST\] search-\d+$/);
const stamp = marker.split('search-')[1], term = 's' + stamp.slice(-3), base = 'http://localhost:8080/api', origin = 'http://localhost:3000';
requireDevTarget(base);
const request = createTestFetch(base), checks = [], faults = [], output = 'output/playwright/search';
const docker = 'C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
await mkdir(output, { recursive: true });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function sql(text) { return execFileSync(docker, ['exec', '-i', 'luminous-dev-mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'], { input: text, encoding: 'utf8' }).trim(); }
function redis(...args) { return execFileSync(docker, ['exec', 'luminous-dev-redis', 'redis-cli', '--raw', ...args], { encoding: 'utf8' }).trim(); }
function pass(text) { checks.push(text); console.log('[PASS] ' + text); }
async function api(path, method = 'GET', body, token, key = randomUUID()) {
  const response = await request(base + path, { method, headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true', 'X-Idempotency-Key': key, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  const raw = response.status === 204 ? '' : await response.text();
  return { status: response.status, bytes: Buffer.byteLength(raw), ...(raw ? JSON.parse(raw) : { data: null }) };
}
async function ok(path, method, body, token) { const result = await api(path, method, body, token); assert.equal(result.status, 200, path + ': ' + JSON.stringify(result)); return result.data; }
async function account(label, username) { return ok('/auth/register', 'POST', { username: username || `${term}_${label}_E2E_TEST`, displayName: `${term}同好${label} ${marker}`, password: 'Password123!' }); }
for (let i = 0; i < 50; i++) { try { if ((await fetch(base + '/users/profile-presets')).ok) break; } catch {} if (i === 49) throw new Error('开发服务未就绪'); await sleep(1000); }
const owner = await account('owner'), friends = [];
for (let i = 0; i < 5; i++) friends.push(await account('u' + i));
const numeric = await account('numeric', '9' + stamp);
const benches = Array.from({ length: 20 }, (_, i) => term + 'b' + String(i).padStart(2, '0'));
const benchText = benches.join(' '), communities = [];
for (let i = 0; i < 5; i++) communities.push(await ok('/communities', 'POST', { slug: `e2e-search-${stamp}-${i}`, name: `${term}社区${i} ${marker}`, description: marker + ' ' + benchText, category: 'Systems' }, owner.token));
for (let i = 0; i < 5; i++) sql(`UPDATE users SET display_name='${marker} ${benches.slice(i * 4, i * 4 + 4).join(' ')}',bio='${marker}' WHERE id=${friends[i].user.id};`);
const community = communities[0], ids = [], tuples = [];
const exactTitle = term + '[E2E_TEST]';
for (let i = 0; i < 1000; i++) {
  const id = (8600000000000000000n + BigInt(stamp) * 1000n + BigInt(i)).toString(); ids.push(id);
  let title = '[E2E_TEST]普通', content = `${marker} ${term} ${benchText}`, votes = 1000;
  if (i === 0) { title = exactTitle; votes = 0; }
  if (i === 1) { title = term + 'X[E2E_TEST]'; votes = 0; }
  if (i === 2) { title = 'X' + term + '[E2E_TEST]'; votes = 0; }
  if (i === 3) { title = 'C++[E2E_TEST]'; votes = 0; }
  if (i === 4) { title = '猫[E2E_TEST]'; votes = 0; }
  if (i === 5) { title = '星河[E2E_TEST]'; content += ' 星河 学习'; votes = 0; }
  tuples.push(`(${id},${owner.user.id},${community.id},'${title}','${content}',TRUE,'NORMAL',NOW(),${votes},0,'DISCUSSION')`);
}
sql(`INSERT INTO posts(id,author_id,community_id,title,content,is_public,status,created_at,votes,comments_count,post_type) VALUES ${tuples.join(',')};`);
const search = async (q, type = 'posts', cursor, token = owner.token) => ok(`/search?q=${encodeURIComponent(q)}&type=${type}&size=20${cursor ? `&cursor=${cursor}` : ''}`, 'GET', null, token);
const suggest = async (q, token = owner.token) => ok(`/search/suggest?q=${encodeURIComponent(q)}`, 'GET', null, token);
const examples = [];
async function first(q, type, expected) { const result = await search(q, type); assert.equal(result.items[0]?.id, expected, q); examples.push({ q, type, expected, actual: result.items[0]?.id }); return result; }
await first(exactTitle, 'posts', ids[0]);
const related = await search(term); assert.ok(related.items.findIndex(hit => hit.id === ids[0]) < related.items.findIndex(hit => hit.id === ids[2]));
assert.ok(related.items.findIndex(hit => hit.id === ids[2]) < related.items.findIndex(hit => hit.id === ids[999]));
await first('C++', 'posts', ids[3]); await first('猫', 'posts', ids[4]); await first('+星河 -学习*', 'posts', ids[5]);
await first('@' + numeric.user.username, 'users', numeric.user.id);
await first(numeric.user.username, 'users', numeric.user.id);
await first('id:' + owner.user.id, 'users', owner.user.id);
await first('c/' + community.name, 'communities', community.id);
await first(community.displayName, 'communities', community.id);
const widthQuery = [...exactTitle].map(char => char >= '!' && char <= '~' ? String.fromCharCode(char.charCodeAt(0) + 0xfee0) : char).join('');
await first(widthQuery, 'posts', ids[0]);
for (const q of ['***', '@', '词'.repeat(51), 'one two three four five six seven']) assert.equal((await api(`/search/suggest?q=${encodeURIComponent(q)}`, 'GET', null, owner.token)).status, 400);
pass('十组固定目标首位命中；精确/前缀/标题优先于高票正文，中文多词、短词、C++、数字用户名/显式ID、社区短名、全角及符号边界正确');
let hints = await suggest(term);
assert.ok(hints.groups.every(group => group.items.length <= 4 && !group.unavailable));
assert.ok(hints.groups.find(group => group.kind === 'posts').hasMore);
assert.ok(!JSON.stringify(hints).includes('passwordHash')); assert.ok(!JSON.stringify(hints).includes('personalGrowth'));
const warmHidden = hints.groups.find(group => group.kind === 'posts').items[0].id;
sql(`UPDATE posts SET is_public=FALSE WHERE id=${warmHidden};`);
assert.ok(!(await suggest(term)).groups.find(group => group.kind === 'posts').items.some(hit => hit.id === warmHidden));
sql(`UPDATE posts SET is_public=TRUE WHERE id=${warmHidden};`);
await suggest('@' + numeric.user.username);
sql(`UPDATE users SET is_banned=TRUE WHERE id=${numeric.user.id};`);
assert.equal((await suggest('@' + numeric.user.username)).groups[0].items.length, 0);
sql(`UPDATE users SET is_banned=FALSE WHERE id=${numeric.user.id};`);
pass('提示每类最多四条，摘要不携带完整资料；热缓存仍重新过滤非公开帖子和封禁账号');
const limiterKey = `luminous:search:limit:user:${numeric.user.id}`;
redis('EVAL', "local t=redis.call('TIME'); local ms=t[1]*1000+math.floor(t[2]/1000); for i=1,120 do redis.call('ZADD',KEYS[1],ms,'e2e-'..i) end; redis.call('EXPIRE',KEYS[1],61); return 1", '1', limiterKey);
assert.equal((await api(`/search/suggest?q=${term}`, 'GET', null, numeric.token)).status, 429);
redis('DEL', limiterKey);
pass('真实搜索限流返回429，开发绕过请求头不能绕过新搜索限额');

const firstPage = await search(term), seen = new Set(firstPage.items.map(hit => hit.id));
assert.equal(firstPage.windowLimited, true); assert.ok(firstPage.nextCursor);
assert.equal((await api(`/search?q=${term}&type=users&cursor=${firstPage.nextCursor}`, 'GET', null, owner.token)).status, 400);
const second = await search(term, 'posts', firstPage.nextCursor);
assert.ok(second.items.every(hit => !seen.has(hit.id)));
redis('DEL', 'luminous:search:v1:snapshot:' + firstPage.nextCursor.split('.')[0]);
assert.equal((await api(`/search?q=${term}&type=posts&cursor=${firstPage.nextCursor}`, 'GET', null, owner.token)).status, 410);
assert.ok((await search(term)).items.length);
pass('分类结果按需分页无重复，二百条窗口有提示；跨类游标拒绝，过期可刷新恢复');

function stats(samples) { const sorted = samples.map(item => item.ms).sort((a, b) => a - b); return { samples: sorted.length, p50Ms: +sorted[Math.ceil(sorted.length * .5) - 1].toFixed(2), p95Ms: +sorted[Math.ceil(sorted.length * .95) - 1].toFixed(2), meanBytes: Math.round(samples.reduce((sum, sample) => sum + sample.bytes, 0) / samples.length) }; }
async function oldSuggestion(q) {
  const start = performance.now();
  const results = await Promise.all([api(`/posts?search=${q}&size=4`, 'GET', null, owner.token), api(`/communities?search=${q}`, 'GET', null, owner.token), api(`/users?search=${q}`, 'GET', null, owner.token)]);
  assert.ok(results.every(result => result.status === 200)); return { ms: performance.now() - start, bytes: results.reduce((sum, result) => sum + result.bytes, 0) };
}
async function newSuggestion(q) { const start = performance.now(); const result = await api(`/search/suggest?q=${q}`, 'GET', null, owner.token); assert.equal(result.status, 200); assert.ok(result.data.groups.every(group => !group.unavailable)); return { ms: performance.now() - start, bytes: result.bytes }; }
for (let i = 0; i < 3; i++) await oldSuggestion(benches[0]);
const old = [], cold = [], warm = [];
for (const q of benches) old.push(await oldSuggestion(q));
for (const q of benches) cold.push(await newSuggestion(q));
for (const q of benches) warm.push(await newSuggestion(q));
const metrics = { environment: 'luminous-dev only', dataset: { posts: 1000, communities: 5, users: 7 }, oldThreeRequests: stats(old), newCandidateCacheMiss: stats(cold), newCandidateCacheHit: stats(warm), notes: '顺序请求，无生产负载；冷指应用候选缓存未命中，未清空MySQL缓冲池；20个同文档频率词，各组20次；排除240ms输入防抖。' };
await writeFile(`${output}/metrics.json`, JSON.stringify(metrics, null, 2)); console.log('[METRICS] ' + JSON.stringify(metrics));
pass('完成旧三请求、新接口候选缓存未命中/命中三组本地响应时间与载荷实测，不作为生产容量结论');

const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
async function capture(page, name) { await page.evaluate(async () => { await document.fonts.ready; scrollTo({ top: 0, behavior: 'instant' }); await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))); }); await page.screenshot({ path: `${output}/${name}.png` }); }
try {
  for (const [device, width, height] of [['desktop', 1440, 1000], ['mobile', 390, 844]]) {
    const context = await browser.newContext({ viewport: { width, height }, reducedMotion: 'reduce' });
    await context.addInitScript(token => localStorage.setItem('luminous_token', token), friends[device === 'desktop' ? 0 : 1].token);
    const page = await context.newPage(); page.setDefaultTimeout(15000); page.on('pageerror', error => faults.push(error.message));
    await page.goto(origin + '/');
    const box = page.getByRole('combobox', { name: '搜索帖子、社区、用户' });
    let suggestions = 0, legacy = 0, aborted = 0;
    page.on('request', req => { const url = new URL(req.url()); if (url.pathname === '/api/search/suggest') suggestions++; if (['/api/users','/api/communities','/api/posts'].includes(url.pathname) && url.searchParams.has('search')) legacy++; });
    page.on('requestfailed', req => { if (new URL(req.url()).pathname === '/api/search/suggest') aborted++; });
    await box.fill(term);
    await page.getByRole('listbox', { name: '搜索结果' }).getByRole('option').first().waitFor();
    assert.equal(suggestions, 1); assert.equal(legacy, 0);
    await capture(page, `suggest-${device}`);
    await page.route('**/api/search/suggest?*', async route => {
      if (new URL(route.request().url()).searchParams.get('q') === 'delay' + term) { await sleep(800); await route.continue().catch(() => {}); }
      else await route.continue();
    });
    aborted = 0;
    const delayed = page.waitForRequest(req => new URL(req.url()).pathname === '/api/search/suggest' && new URL(req.url()).searchParams.get('q') === 'delay' + term);
    await box.fill('delay' + term); await delayed;
    await box.fill(term); await page.getByRole('option').filter({ hasText: term }).first().waitFor();
    await page.waitForTimeout(300); assert.ok(aborted > 0);
    await page.unroute('**/api/search/suggest?*');
    await page.route('**/api/search/suggest?*', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, error: { code: 'SEARCH_UNAVAILABLE', message: '搜索暂不可用，请稍后重试' } }) }), { times: 1 });
    await box.fill(term + ' '); await page.getByRole('button', { name: '重试搜索' }).waitFor();
    await page.getByRole('button', { name: '重试搜索' }).click(); await page.getByRole('option').first().waitFor();
    await box.press('Escape'); await box.focus();
    await box.evaluate(element => element.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true })));
    const beforeComposition = suggestions;
    await box.fill('猫'); await page.waitForTimeout(350);
    assert.equal(suggestions, beforeComposition);
    const commitAllowed = await box.evaluate(element => { const event = new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true, cancelable: true }); element.dispatchEvent(event); return !event.defaultPrevented; });
    assert.equal(commitAllowed, true);
    await box.evaluate(element => element.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: '猫' })));
    await page.getByRole('listbox', { name: '搜索结果' }).getByRole('option').first().waitFor();
    await box.fill(term); await page.getByRole('option').filter({ hasText: term }).first().waitFor();
    await box.press('Enter');
    await page.waitForURL(/\/search\?/);
    const region = page.getByRole('region', { name: '帖子搜索结果' });
    await region.locator('a').first().waitFor(); assert.equal(await region.locator('a').count(), 20);
    await capture(page, `results-${device}`);
    await region.getByRole('button', { name: '加载更多结果' }).click();
    await page.waitForFunction(() => document.querySelector('section[aria-label="帖子搜索结果"]').querySelectorAll('a').length === 40);
    const count = await region.locator('a').count(); assert.equal(count, 40);
    await page.getByRole('button', { name: '社区', exact: true }).click(); await page.getByRole('region', { name: '社区搜索结果' }).locator('a').first().waitFor();
    await page.getByRole('button', { name: '用户', exact: true }).click(); await page.getByRole('region', { name: '用户搜索结果' }).locator('a').first().waitFor();
    await page.goto(`${origin}/search?q=${encodeURIComponent('不存在' + stamp)}`); await page.getByText('没有找到匹配内容', { exact: true }).waitFor(); await capture(page, `empty-${device}`);
    await page.goto(`${origin}/search?q=${term}`); await page.getByRole('region', { name: '帖子搜索结果' }).locator('a').first().waitFor();
    const initial = await search(term); redis('DEL', 'luminous:search:v1:snapshot:' + initial.nextCursor.split('.')[0]);
    await page.getByRole('button', { name: '加载更多结果' }).click(); await page.getByRole('button', { name: '刷新结果' }).waitFor(); await page.getByRole('button', { name: '刷新结果' }).click();
    await page.getByRole('region', { name: '帖子搜索结果' }).locator('a').first().waitFor();
    await page.evaluate(() => document.documentElement.classList.add('dark'));
    const hoveredResult = page.getByRole('region', { name: '帖子搜索结果' }).locator('a').nth(1);
    await hoveredResult.hover();
    assert.equal(await hoveredResult.locator('h2').evaluate(element => getComputedStyle(element).color), 'rgb(147, 180, 255)');
    await capture(page, `dark-${device}`);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await context.close(); pass(`${device}：一次提示请求、不调用旧接口、旧请求取消和503重试；中文输入完成后搜索、回车进入结果；分类/续页/过期恢复/空态/深色正常，无横向溢出`);
  }
} finally { await browser.close(); }
assert.deepEqual(faults, []);
await writeFile(`${output}/result.json`, JSON.stringify({ marker, examples, checks, faults }, null, 2));
