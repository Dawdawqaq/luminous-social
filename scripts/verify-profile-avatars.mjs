import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { createTestFetch, readCaptchaOffset, requireDevTarget } from './test-api-auth.mjs';

const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || '', /^\[E2E_TEST\] avatars-\d+$/);
const stamp = marker.split('avatars-')[1];
const origin = 'http://localhost:3000', base = 'http://localhost:8080/api';
requireDevTarget(base);
const request = createTestFetch(base), checks = [], faults = [];
const output = 'output/playwright/profile-avatars';
await mkdir(output, { recursive: true });
const approved = JSON.parse(await readFile('assets/characters/avatar-cos-v1/approved-v1.json', 'utf8'));
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
function pass(name) { checks.push(name); console.log('[PASS] ' + name); }
async function api(path, method = 'GET', body, token) {
  const response = await request(base + path, { method, headers: {
    'Content-Type': 'application/json', 'X-Bypass-Rate-Limit': 'true',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }, ...(body ? { body: JSON.stringify(body) } : {}) });
  return { status: response.status, ...(await response.json()) };
}
const catalog = await api('/users/profile-presets');
assert.equal(catalog.status, 200);
assert.deepEqual(catalog.data.avatars.map(avatar => avatar.id), approved.avatars.map(avatar => avatar.id));
for (const preset of catalog.data.avatars) {
  const response = await fetch(origin + preset.url);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /image\/webp/);
  assert.ok((await response.arrayBuffer()).byteLength > 1000);
}
pass('公开目录与九款定稿一致，全部 WebP 通过真实静态服务加载');
const legacy = await api('/auth/register', 'POST', { username: `E2E_TEST_av_old_${stamp}`, displayName: marker + ' legacy', password: 'Password123!' });
assert.equal(legacy.status, 200);
assert.ok(catalog.data.avatars.some(preset => preset.url === legacy.data.user.avatar));
const invalidName = `E2E_TEST_av_bad_${stamp}`;
const invalid = await api('/auth/register', 'POST', { username: invalidName, displayName: marker + ' invalid', password: 'Password123!', avatarPresetId: 'naruto' });
assert.equal(invalid.status, 400);
assert.equal(invalid.error.code, 'INVALID_AVATAR_PRESET');
assert.equal((await api(`/users/${invalidName}?identifierType=username`)).status, 404);
pass('旧客户端随机头像兼容；未入选预设拒绝且未创建账号');

const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
async function solveCaptcha(page) {
  await page.getByRole('button', { name: '拖动安全验证滑块' }).waitFor();
  await page.waitForFunction(() => !!document.querySelector('img[alt="待对齐拼图"]'));
  // 等待切换步骤的焦点定位完成，避免自动聚焦与拖动时的滚动相互干扰。
  await pause(200);
  const challenge = page.challenge;
  const session = (await page.context().cookies()).find(cookie => cookie.name === 'luminous_captcha_session')?.value;
  const offset = await readCaptchaOffset(session, challenge.challengeId);
  const slider = page.getByRole('button', { name: '拖动安全验证滑块' });
  await slider.scrollIntoViewIfNeeded();
  await pause(100);
  const box = await slider.boundingBox(), width = await slider.evaluate(button => button.parentElement.clientWidth);
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + offset * (width - 44) / (challenge.width - challenge.pieceSize), box.y + box.height / 2, { steps: 16 });
  await pause(350);
  const verification = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/captcha/verify');
  await page.mouse.up();
  const result = await (await verification).json();
  assert.equal(result.success, true, JSON.stringify(result.error));
  await page.getByText('安全验证通过', { exact: true }).waitFor();
}
async function settleImages(page) {
  await page.waitForFunction(() => [...document.querySelectorAll('[role="dialog"] img')].every(image => image.complete && image.naturalWidth > 0));
  await page.evaluate(() => document.fonts.ready);
}
async function assertFits(page) {
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  const dialog = page.getByRole('dialog');
  assert.ok(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1));
  const box = await dialog.boundingBox();
  assert.ok(box.x >= 0 && box.x + box.width <= page.viewportSize().width + 1);
}
try {
  for (const [device, width, height, firstId, nextId] of [
    ['desktop', 1440, 1000, 'pharaoh', 'totoro'], ['mobile', 390, 844, 'little-prince', 'liberty'],
  ]) {
    const context = await browser.newContext({ viewport: { width, height }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    page.on('pageerror', error => faults.push(error.message));
    page.on('response', async response => {
      if (new URL(response.url()).pathname === '/api/auth/captcha' && response.ok()) page.challenge = (await response.json()).data;
    });
    await page.goto(origin);
    await page.getByRole('button', { name: '登录', exact: true }).first().click();
    await page.getByRole('button', { name: /立即注册/ }).click();
    let dialog = page.getByRole('dialog');
    const username = `E2E_TEST_av_${device === 'desktop' ? 'd' : 'm'}_${stamp}`;
    await dialog.getByLabel('用户名', { exact: true }).fill(username);
    await dialog.getByLabel('昵称', { exact: true }).fill(marker + ' ' + device);
    await dialog.getByLabel('密码', { exact: true }).fill('Password123!');
    await dialog.getByRole('button', { name: '下一步：选择头像', exact: true }).click();
    assert.equal(await dialog.getByRole('radio').count(), 9);
    const first = catalog.data.avatars.find(avatar => avatar.id === firstId);
    const next = catalog.data.avatars.find(avatar => avatar.id === nextId);
    await dialog.getByRole('radio', { name: first.name, exact: true }).locator('..').click();
    await settleImages(page);
    await assertFits(page);
    await page.screenshot({ path: `${output}/register-${device}.png` });
    await dialog.getByRole('button', { name: '返回账号资料', exact: true }).click();
    assert.equal(await dialog.getByLabel('用户名', { exact: true }).inputValue(), username);
    await dialog.getByRole('button', { name: '下一步：选择头像', exact: true }).click();
    assert.ok(await dialog.getByRole('radio', { name: first.name, exact: true }).isChecked());
    await solveCaptcha(page);
    const registered = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/register' && response.request().method() === 'POST');
    await dialog.getByRole('button', { name: '使用这个头像，完成注册', exact: true }).click();
    const auth = (await (await registered).json()).data;
    assert.equal(auth.user.avatar, first.url);
    await page.getByRole('button', { name: '打开用户菜单', exact: true }).waitFor();
    await page.goto(`${origin}/u/${username}`);
    const header = page.locator('header.panel');
    await header.getByRole('button', { name: '更换头像', exact: true }).click();
    dialog = page.getByRole('dialog');
    await dialog.getByRole('radio', { name: catalog.data.avatars[0].name, exact: true }).focus();
    await page.keyboard.press('ArrowRight');
    assert.ok(await dialog.getByRole('radio', { name: catalog.data.avatars[1].name, exact: true }).isChecked());
    await dialog.getByRole('radio', { name: next.name, exact: true }).locator('..').click();
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    assert.equal((await api('/auth/me', 'GET', null, auth.token)).data.avatar, first.url);
    await header.getByRole('button', { name: '更换头像', exact: true }).click();
    await dialog.getByRole('radio', { name: next.name, exact: true }).locator('..').click();
    await settleImages(page);
    await assertFits(page);
    await page.screenshot({ path: `${output}/picker-${device}.png` });
    await page.evaluate(() => document.documentElement.classList.add('dark'));
    await page.screenshot({ path: `${output}/picker-dark-${device}.png` });
    await page.evaluate(() => document.documentElement.classList.remove('dark'));
    await page.route('**/api/auth/me', async route => {
      if (route.request().method() !== 'PUT') return route.continue();
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, error: { code: 'TEMPORARY', message: '保存暂时失败，请重试。' } }) });
    }, { times: 1 });
    await dialog.getByRole('button', { name: '保存头像', exact: true }).click();
    await dialog.getByRole('alert').getByText('保存暂时失败，请重试。', { exact: true }).waitFor();
    assert.ok(await dialog.getByRole('radio', { name: next.name, exact: true }).isChecked());
    const saved = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/me' && response.request().method() === 'PUT');
    await dialog.getByRole('button', { name: '保存头像', exact: true }).click();
    assert.equal((await (await saved).json()).data.avatar, next.url);
    await dialog.waitFor({ state: 'hidden' });
    assert.equal(await header.getByRole('button', { name: '更换头像', exact: true }).locator('img').getAttribute('src'), next.url);
    assert.equal(JSON.parse(await page.evaluate(() => localStorage.getItem('luminous_user'))).avatar, next.url);
    await page.reload();
    await header.getByRole('button', { name: '更换头像', exact: true }).waitFor();
    assert.equal(await header.getByRole('button', { name: '更换头像', exact: true }).locator('img').getAttribute('src'), next.url);
    const avatarBox = await header.getByRole('button', { name: '更换头像', exact: true }).boundingBox();
    assert.ok(Math.abs(avatarBox.width - avatarBox.height) <= 1, '头像按钮保持方形，修改入口紧贴头像');
    await page.screenshot({ path: `${output}/profile-${device}.png` });
    pass(`${device}：两步注册保留选择、取消不保存、失败保留草稿可重试、切换后刷新一致且无横向溢出`);

    if (device === 'desktop') {
      await header.getByRole('button', { name: '更换头像', exact: true }).click();
      await dialog.getByLabel('头像图片文件', { exact: true }).setInputFiles('frontend/public/profile-presets/avatars/pearl.webp');
      await dialog.getByRole('button', { name: '保存头像', exact: true }).click();
      await dialog.waitFor({ state: 'hidden' });
      const uploaded = (await api('/auth/me', 'GET', null, auth.token)).data;
      assert.ok(uploaded.avatar && !uploaded.avatar.startsWith('/profile-presets/'));
      await header.getByRole('button', { name: '更换头像', exact: true }).click();
      await dialog.getByRole('radio', { name: first.name, exact: true }).locator('..').click();
      await dialog.getByRole('button', { name: '保存头像', exact: true }).click();
      await dialog.waitFor({ state: 'hidden' });
      assert.equal((await api('/auth/me', 'GET', null, auth.token)).data.avatar, first.url);
      pass('上传头像仍可用，上传后可以再次切回预设');
    }
    await page.goto(`${origin}/u/${legacy.data.user.username}`);
    await page.getByRole('heading', { name: legacy.data.user.displayName, exact: true }).waitFor();
    assert.equal(await page.locator('header.panel').getByRole('button', { name: '更换头像', exact: true }).count(), 0);
    pass(`${device}：他人主页无头像修改入口`);
    await context.close();
  }
  assert.deepEqual(faults, []);
  await writeFile(`${output}/result.json`, JSON.stringify({ marker, checks, faults }, null, 2));
} finally {
  await browser.close();
}
