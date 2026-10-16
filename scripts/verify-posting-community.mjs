import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { createTestFetch, requireDevTarget } from './test-api-auth.mjs';

const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || '', /^\[E2E_TEST\] posting-\d+$/);
const stamp = marker.split('posting-')[1], base = 'http://localhost:8080/api', origin = 'http://localhost:3000';
requireDevTarget(base);
const request = createTestFetch(base), checks = [], faults = [], output = 'output/playwright/posting-community';
const docker = 'C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
const image = await readFile('frontend/public/brand/luminous-star-soft.png');
await mkdir(output, { recursive: true });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function sql(text) { return execFileSync(docker, ['exec', '-i', 'luminous-dev-mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'], { input: text, encoding: 'utf8' }).trim(); }
function pass(text) { checks.push(text); console.log('[PASS] ' + text); }
async function api(path, method = 'GET', body, token, key = randomUUID()) {
  const response = await request(base + path, { method, headers: { 'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true', 'X-Idempotency-Key': key, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  return { status: response.status, wait: Number(response.headers.get('Retry-After')), ...(response.status === 204 ? { data: null } : await response.json()) };
}
async function ok(path, method, body, token, key) { const result = await api(path, method, body, token, key); assert.ok([200, 204].includes(result.status), path + ': ' + JSON.stringify(result)); return result.data; }
async function captureTop(page, path) {
  await page.evaluate(async () => { await document.fonts.ready; window.scrollTo({ top: 0, behavior: 'instant' }); await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))); });
  await page.screenshot({ path, fullPage: true });
}
async function account(label) { return ok('/auth/register', 'POST', { username: `E2E_TEST_p${label}_${stamp.slice(-9)}`, displayName: `${marker} ${label}`, password: 'Password123!' }); }
for (let attempt = 0; attempt < 50; attempt++) { try { if ((await fetch(base + '/users/profile-presets')).ok) break; } catch {} if (attempt === 49) throw new Error('开发服务未就绪'); await sleep(1000); }
const owner = await account('owner'), actor = await account('actor'), racer = await account('racer');
const communities = [];
for (let i = 0; i < 14; i++) communities.push(await ok('/communities', 'POST', { slug: `e2e-posting-${stamp}-${i}`, name: `${marker} 社区${i}`, description: `${marker} 兴趣交流`, category: i < 12 ? 'Systems' : 'Living' }, owner.token));
const community = communities[0];
assert.equal((await ok(`/communities/${community.name}`)).category, 'Systems');
let cursor, all = [];
do { const page = await ok(`/communities/browse?search=${encodeURIComponent(marker)}&size=5${cursor ? `&cursor=${cursor}` : ''}`); all.push(...page.items); cursor = page.nextCursor; } while (cursor);
assert.equal(all.length, 14); assert.equal(new Set(all.map(item => item.id)).size, 14);
assert.equal((await ok(`/communities/browse?search=${encodeURIComponent(marker)}&category=Systems&size=30`)).items.length, 12);
assert.equal((await ok(`/communities/browse?scope=joined`, 'GET', null, actor.token)).items.length, 0);
assert.equal((await api('/communities/browse?size=31')).status, 400);
assert.equal((await api('/communities/browse?cursor=bad')).status, 400);
pass('分类实际保存；服务端搜索、分类及已加入筛选正确；编号分页无重复，非法参数被拒绝');
const body = { community: community.name, title: '[E2E_TEST] 发帖', content: marker, isPublic: true };
assert.equal((await api('/posts', 'POST', body, actor.token)).status, 403);
assert.equal(sql(`SELECT COUNT(*) FROM post_publications WHERE user_id=${actor.user.id};`), '0');
await ok(`/communities/${community.name}/join`, 'POST', null, actor.token);
assert.equal((await api('/posts', 'POST', { ...body, content: '' }, actor.token)).status, 400);
assert.equal((await api('/posts', 'POST', { ...body, title: '标题'.repeat(8) }, actor.token)).status, 400);
assert.equal((await api('/posts', 'POST', { ...body, image: '/not-uploaded.png' }, actor.token)).status, 400);
assert.equal(sql(`SELECT COUNT(*) FROM post_publications WHERE user_id=${actor.user.id};`), '0');
const form = new FormData(); form.append('file', new Blob([image], { type: 'image/png' }), `${marker}.png`);
const uploadedResponse = await request(base + '/uploads/images', { method: 'POST', headers: { Authorization: `Bearer ${owner.token}`, 'X-Idempotency-Key': randomUUID() }, body: form });
assert.equal(uploadedResponse.status, 200); const uploaded = (await uploadedResponse.json()).data;
assert.equal((await api('/posts', 'POST', { ...body, content: '', image: uploaded.url }, actor.token)).status, 400);
const imagePost = await ok('/posts', 'POST', { ...body, content: '', image: uploaded.url, type: 'FIELD_NOTE' }, owner.token);
assert.equal(imagePost.image, uploaded.url); assert.equal(imagePost.content, ''); assert.equal(imagePost.type, 'DISCUSSION');
pass('未加入不能发帖；空内容/越界/非本人图片失败不占额度；标题加单图可发布，旧类型入参不再改变新帖分类');
await ok(`/communities/${community.name}/join`, 'POST', null, racer.token);
const race = await Promise.all([api('/posts', 'POST', body, racer.token), api('/posts', 'POST', body, racer.token)]);
assert.deepEqual(race.map(result => result.status).sort(), [200, 429]);
const created = race.find(result => result.status === 200).data;
assert.ok(race.find(result => result.status === 429).wait > 0);
await ok(`/posts/${created.id}`, 'DELETE', null, racer.token);
assert.equal((await api('/posts', 'POST', body, racer.token)).status, 429);
assert.equal(sql(`SELECT COUNT(*) FROM post_publications WHERE user_id=${racer.user.id};`), '1');
pass('真实并发只成功一条；429带等待时间；删除帖子不返还成功发布额度');
assert.equal((await api(`/communities/${community.name}`, 'PUT', { name: '越权', description: marker, rules: [] }, actor.token)).status, 403);
assert.equal((await api(`/communities/${community.name}`, 'PUT', { name: marker, description: marker, rules: Array(11).fill('规则') }, owner.token)).status, 400);
await ok(`/communities/${community.name}`, 'PUT', { name: community.displayName, description: marker, rules: ['友好交流', '分享真实发现'], iconUrl: uploaded.url, bannerUrl: uploaded.url }, owner.token);
assert.deepEqual((await ok(`/communities/${community.name}`)).rules, ['友好交流', '分享真实发现']);
pass('社区管理限制本人权限、约定条数；图标、封面及约定可持久化并重新读取');
const ids = [];
for (let i = 0; i < 22; i++) {
  const id = (8700000000000000000n + BigInt(stamp) * 100n + BigInt(i)).toString(); ids.push(id);
  sql(`INSERT INTO posts(id,author_id,community_id,title,content,is_public,status,created_at,votes,comments_count,post_type) VALUES(${id},${owner.user.id},${community.id},'[E2E_TEST] P${String(i).padStart(2,'0')}','${marker}',TRUE,'NORMAL',NOW()-INTERVAL ${i + 1} MINUTE,1,0,'DISCUSSION'); INSERT INTO vote_states(post_id,user_id,desired_value,accepted_version,applied_value,applied_version) VALUES(${id},${actor.user.id},1,0,1,0);`);
}
let postCursor, posts = [];
do { const page = await ok(`/communities/${community.name}/posts?size=10${postCursor ? `&cursor=${postCursor}` : ''}`); posts.push(...page.items); postCursor = page.nextCursor; } while (postCursor);
assert.equal(new Set(posts.map(post => post.id)).size, 23); assert.equal(posts.length, 23);
assert.equal((await api(`/communities/${community.name}/posts?cursor=bad`)).status, 400);
pass('社区最新流可继续加载超过原先20条，分页去重；窗口热门复用稳定榜单');

const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  for (const [device, width, height] of [['desktop', 1440, 1000], ['mobile', 390, 844]]) {
    const poster = await account(device);
    const context = await browser.newContext({ viewport: { width, height }, reducedMotion: 'reduce' });
    await context.addInitScript(token => { if (!localStorage.getItem('luminous_token')) localStorage.setItem('luminous_token', token); }, poster.token);
    const page = await context.newPage(); page.setDefaultTimeout(15000);
    page.on('pageerror', error => faults.push(error.message)); page.on('dialog', dialog => dialog.accept());
    await page.goto(`${origin}/salons`);
    await page.getByRole('textbox', { name: '筛选社区' }).fill(marker);
    await page.locator('.community-grid a').first().waitFor();
    await page.waitForFunction(() => document.querySelectorAll('.community-grid a').length === 12);
    await captureTop(page, `${output}/discover-${device}.png`);
    await page.getByRole('button', { name: '加载更多社区', exact: true }).click();
    await page.waitForFunction(() => document.querySelectorAll('.community-grid a').length === 14);
    await page.getByLabel('兴趣分类').selectOption('Systems');
    await page.waitForFunction(() => document.querySelectorAll('.community-grid a').length === 12);
    await page.goto(`${origin}/s/${community.name}`);
    await page.locator('article').first().waitFor();
    assert.equal(await page.locator('article').count(), 10);
    await page.getByRole('button', { name: '加载更多帖子' }).click();
    await page.waitForFunction(() => document.querySelectorAll('article').length === 20);
    await page.getByRole('button', { name: '近七天热门' }).click();
    await page.locator('article').first().waitFor();
    await captureTop(page, `${output}/community-${device}.png`);
    await page.locator('header.panel').getByRole('button', { name: '发帖', exact: true }).click();
    let dialog = page.getByRole('dialog', { name: '分享新发现', exact: true });
    await dialog.getByRole('textbox', { name: '标题', exact: true }).waitFor();
    assert.equal(await dialog.getByText('内容类型', { exact: true }).count(), 0);
    await dialog.getByRole('textbox', { name: '标题', exact: true }).fill('[E2E_TEST] UI');
    await dialog.getByRole('textbox', { name: '正文', exact: true }).fill(marker);
    await dialog.getByRole('button', { name: '选择猫猫表情' }).click();
    await page.getByRole('dialog', { name: '猫猫表情', exact: true }).getByRole('button').first().click();
    await dialog.getByRole('textbox', { name: '正文', exact: true }).evaluate((element, b64) => {
      const data = new DataTransfer(); data.items.add(new File([Uint8Array.from(atob(b64), char => char.charCodeAt(0))], '[E2E_TEST] paste.png', { type: 'image/png' }));
      element.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
    }, image.toString('base64'));
    await dialog.getByAltText('待发布图片', { exact: true }).waitFor();
    assert.equal(await dialog.getByRole('button', { name: '发布帖子', exact: true }).isDisabled(), true);
    await dialog.getByRole('button', { name: '关闭窗口' }).click();
    await dialog.getByRole('button', { name: '保留并关闭' }).click();
    await dialog.waitFor({ state: 'hidden' });
    await page.evaluate(token => localStorage.setItem('luminous_token', token), owner.token);
    await page.reload();
    await page.locator('header.panel').getByRole('button', { name: '发帖', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '分享新发现', exact: true });
    await dialog.getByRole('textbox', { name: '标题', exact: true }).waitFor();
    assert.equal(await dialog.getByRole('textbox', { name: '标题', exact: true }).inputValue(), '');
    assert.equal(await dialog.getByAltText('待发布图片', { exact: true }).count(), 0);
    await dialog.getByRole('button', { name: '关闭窗口' }).click();
    await page.evaluate(token => localStorage.setItem('luminous_token', token), poster.token);
    await page.reload();
    await page.locator('header.panel').getByRole('button', { name: '发帖', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '分享新发现', exact: true });
    await dialog.getByAltText('待发布图片', { exact: true }).waitFor();
    assert.equal(await dialog.getByRole('textbox', { name: '标题', exact: true }).inputValue(), '[E2E_TEST] UI');
    assert.ok((await dialog.getByRole('textbox', { name: '正文', exact: true }).innerText()).includes(marker));
    await dialog.getByRole('button', { name: '加入社区', exact: true }).click();
    await dialog.getByText('已加入', { exact: true }).first().waitFor();
    await dialog.getByRole('button', { name: '收起选择', exact: true }).click();
    await page.screenshot({ path: `${output}/editor-${device}.png` });
    await dialog.getByRole('button', { name: '更换社区', exact: true }).click();
    const communitySearch = dialog.getByRole('searchbox', { name: '搜索发布社区', exact: true });
    await communitySearch.fill(marker);
    await dialog.locator('form').evaluate(form => { window.__postingSubmitCount = 0; form.addEventListener('submit', () => { window.__postingSubmitCount++; }); });
    const accidentalPosts = [];
    const observePost = req => { if (req.method() === 'POST' && new URL(req.url()).pathname === '/api/posts') accidentalPosts.push(req.url()); };
    page.on('request', observePost);
    await communitySearch.press('Enter');
    await page.waitForTimeout(500);
    assert.deepEqual(accidentalPosts, []);
    assert.equal(await page.evaluate(() => window.__postingSubmitCount), 0);
    assert.equal(await dialog.isVisible(), true);
    assert.equal(await dialog.getByRole('textbox', { name: '标题', exact: true }).inputValue(), '[E2E_TEST] UI');
    const composingAllowed = await communitySearch.evaluate(element => {
      const event = new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true, cancelable: true });
      element.dispatchEvent(event); return !event.defaultPrevented;
    });
    assert.equal(composingAllowed, true);
    page.off('request', observePost);
    await dialog.getByRole('button', { name: '收起选择', exact: true }).click();

    await page.route('**/api/uploads/images', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, error: { code: 'UPLOAD_FAILED', message: '图片上传失败，请重试' } }) }), { times: 1 });
    await dialog.getByRole('button', { name: '发布帖子', exact: true }).click();
    await dialog.getByRole('alert').filter({ hasText: '图片上传失败' }).waitFor();
    await dialog.getByAltText('待发布图片', { exact: true }).waitFor();
    await page.unroute('**/api/uploads/images');
    await dialog.getByRole('button', { name: '重试发布', exact: true }).click();
    await page.waitForURL(/\/post\/\d+$/);
    const publishedId = page.url().split('/').pop();
    const published = await ok(`/posts/${publishedId}`);
    assert.ok(published.image); assert.ok(published.content.includes(marker));
    await page.getByRole('button', { name: '返回上一页', exact: true }).click();
    await page.waitForURL(new RegExp(`/s/${community.name}$`));
    await page.locator('header.panel').getByRole('button', { name: '发帖', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '分享新发现', exact: true });
    await dialog.getByRole('textbox', { name: '标题', exact: true }).waitFor();
    assert.equal(await dialog.getByRole('textbox', { name: '标题', exact: true }).inputValue(), '');
    await dialog.getByRole('button', { name: /秒后可发布/ }).waitFor();
    await dialog.getByRole('textbox', { name: '标题', exact: true }).fill('[E2E_TEST] 放弃');
    await dialog.getByRole('button', { name: '关闭窗口' }).click();
    await dialog.getByRole('button', { name: '放弃草稿', exact: true }).click();
    await page.locator('header.panel').getByRole('button', { name: '发帖', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '分享新发现', exact: true });
    await dialog.getByRole('textbox', { name: '标题', exact: true }).waitFor();
    assert.equal(await dialog.getByRole('textbox', { name: '标题', exact: true }).inputValue(), '');
    await dialog.getByRole('button', { name: '关闭窗口' }).click();
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.evaluate(() => localStorage.setItem('luminous_v2_theme', 'dark'));
    await page.reload();
    await page.locator('article').first().waitFor();
    await captureTop(page, `${output}/dark-${device}.png`);
    await context.close();
    pass(`${device}：社区搜索/分类/续页、最新/热门；单图粘贴、猫猫表情、关闭保护/放弃、账号隔离及刷新恢复；加入后发布、上传失败重试、成功跳转/返回和草稿清除、按钮冷却；搜索回车不提交且保留中文组合输入，页面无横向溢出`);
  }
} finally { await browser.close(); }
assert.deepEqual(faults, []);
await writeFile(`${output}/result.json`, JSON.stringify({ marker, checks, faults }, null, 2));
