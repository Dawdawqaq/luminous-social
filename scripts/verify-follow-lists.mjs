import { pathToFileURL } from "node:url";
import { mkdir, writeFile } from "node:fs/promises";
import assert from "node:assert/strict";
import { createTestFetch } from "./test-api-auth.mjs";

const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const base = "http://localhost:8080/api";
const origin = "http://localhost:3000";
const request = createTestFetch(base);
const marker = process.env.E2E_RUN_MARKER;
if (!/^\[E2E_TEST\] follow-\d+$/.test(marker || "")) throw new Error("必须提供独立测试批次标记");
const stamp = marker.split("follow-")[1];
const output = "output/playwright/follow-lists";
await mkdir(output, { recursive: true });
const checks = [];
function record(label) {
  checks.push(label);
  console.log(`[PASS] ${label}`);
}
async function api(path, method = "GET", body, token) {
  for(let attempt=0;attempt<3;attempt++) {
  const response = await request(base + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-Bypass-Rate-Limit": "true",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const result = await response.json();
  if(response.status===429 && path.endsWith('/follow') && attempt<2) {
    await new Promise(resolve=>setTimeout(resolve,Math.max(1,Number(response.headers.get('Retry-After') || 1))*1000+30));
    continue;
  }
  assert.equal(response.status, 200, `${path}: ${JSON.stringify(result)}`);
  assert.equal(result.success, true);
  return result.data;
  }
}

// 正式开发接口生成带标记数据；包装脚本在成功或失败后都精确清理本批次。
const accounts = [];
for (let index = 0; index < 23; index++) {
  accounts.push(await api("/auth/register", "POST", {
    username: `e2e_f_${stamp}_${index}`,
    displayName: `${marker} 用户${index}`,
    password: "TestPassword123!",
  }));
}
const owner = accounts[0];
for (const peer of accounts.slice(1, 22)) {
  await api(`/users/${peer.user.username}/follow`, "POST", undefined, owner.token);
}
await api(`/users/${owner.user.username}/follow`, "POST", undefined, accounts[1].token);
const path = `/users/${owner.user.username}`;
const first = await api(path + "/following?page=1&size=20");
const second = await api(path + "/following?page=2&size=20");
assert.equal(first.total, 21);
assert.equal(first.items.length, 20);
assert.equal(first.hasNext, true);
assert.equal(second.items.length, 1);
assert.equal(second.hasNext, false);
assert.equal(new Set([...first.items, ...second.items].map((item) => item.id)).size, 21);
assert.equal(first.items[0].username, accounts[21].user.username);
assert.deepEqual(Object.keys(first.items[0]).sort(), ["avatar", "bio", "displayName", "id", "isFollowing", "username"]);
assert.equal(first.items[0].isFollowing,false);
const fans = await api(path + "/followers");
assert.equal(fans.items[0].username, accounts[1].user.username);
assert.equal(fans.total, 1);
record("真实 MySQL：两个方向、分页无重复、新关注排序、公开字段");

for (const [suffix, status] of [["/following?page=0", 400], ["/followers?size=51", 400], ["/following?page=2&size=0", 400]]) {
  assert.equal((await fetch(base + path + suffix)).status, status);
}
assert.equal((await fetch(base + `/users/e2e_missing_${stamp}/followers`)).status, 404);
assert.equal((await api(path + "/following?page=3&size=20")).items.length, 0);
record("真实 HTTP：参数错误 400、用户不存在 404、越界页为空");
await api(`/users/${accounts[21].user.username}/follow`, "POST", undefined, owner.token);
assert.equal((await api(path + "/following")).total, 21);
await api(`/users/${accounts[21].user.username}/follow`, "DELETE", undefined, owner.token);
assert.equal((await api(path + "/following")).total, 20);
await api(`/users/${accounts[21].user.username}/follow`, "POST", undefined, owner.token);
assert.equal((await api(path)).stats.following, 21);
record("真实关系变化：重复关注、取消、重新关注与统计一致");

const browser = await chromium.launch({ channel: "chrome", headless: true });
const faults = [];
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: "reduce" });
  await context.addInitScript(({ token }) => localStorage.setItem("luminous_token", token), { token: owner.token });
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on("pageerror", (error) => faults.push(error.message));
  await page.goto(`${origin}/u/${owner.user.username}`);
  await page.getByRole("link", { name: "我的帖子", exact: true }).waitFor();
  await page.getByRole("link", { name: "查看关注列表，21 人", exact: true }).click();
  await page.getByText("第 1 页 · 共 21 人", { exact: true }).waitFor();
  const content = page.locator("section.panel");
  assert.equal(await content.locator("li").count(), 20);
  await page.screenshot({ path: `${output}/desktop.png`, fullPage: true });
  await page.getByRole("button", { name: "下一页", exact: true }).click();
  await page.getByText("第 2 页 · 共 21 人", { exact: true }).waitFor();
  assert.equal(await content.locator("li").count(), 1);
  assert.equal(await page.getByRole("button", { name: "下一页", exact: true }).isDisabled(), true);
  await page.getByRole("button", { name: "上一页", exact: true }).click();
  await page.getByText("第 1 页 · 共 21 人", { exact: true }).waitFor();
  await page.reload();
  await page.getByText("第 1 页 · 共 21 人", { exact: true }).waitFor();
  record("真实浏览器：顶部入口、关注列表、上一页/下一页、刷新保留列表");

  await page.getByRole("link", { name: "查看粉丝列表，1 人", exact: true }).click();
  await page.getByText("第 1 页 · 共 1 人", { exact: true }).waitFor();
  await content.getByRole("link", { name: `查看 ${accounts[1].user.displayName} 的个人主页`, exact: true }).click();
  await page.getByRole("heading", { name: accounts[1].user.displayName, exact: true }).waitFor();
  await page.goBack();
  await page.getByText("第 1 页 · 共 1 人", { exact: true }).waitFor();
  record("真实浏览器：粉丝列表、用户主页跳转、返回恢复原列表");

  await page.getByRole('link',{name:'关注列表',exact:true}).click();
  await page.getByText('第 1 页 · 共 21 人',{exact:true}).waitFor();
  await page.getByRole('button',{name:'下一页',exact:true}).click();
  await page.getByText('第 2 页 · 共 21 人',{exact:true}).waitFor();
  const lastRow=content.locator('li').first();
  const removedId=await lastRow.getAttribute('data-follow-user-id');
  await lastRow.getByRole('button',{name:/取消关注/}).click();
  await page.getByText('第 1 页 · 共 20 人',{exact:true}).waitFor();
  await new Promise(resolve=>setTimeout(resolve,1100));
  const removed=accounts.find(actor=>actor.user.id===removedId);
  assert.ok(removed);
  await api(`/users/${removed.user.username}/follow`,'POST',undefined,owner.token);
  await page.reload();
  await page.getByText('第 1 页 · 共 21 人',{exact:true}).waitFor();
  record('真实浏览器：末页取消最后一人自动回上一页，恢复关系后刷新人数一致');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("link", { name: "关注列表", exact: true }).click();
  await page.getByText("第 1 页 · 共 21 人", { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.screenshot({ path: `${output}/mobile.png`, fullPage: true });
  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await page.screenshot({ path: `${output}/mobile-dark.png`, fullPage: true });
  record("真实浏览器：手机与深色主题，页面无横向溢出");

  await page.goto(`${origin}/u/${accounts[22].user.username}?tab=following`);
  await page.getByRole("heading", { name: "还没有关注任何人", exact: true }).waitFor();
  await page.getByRole("link", { name: "粉丝列表", exact: true }).click();
  await page.getByRole("heading", { name: "还没有粉丝", exact: true }).waitFor();
  record("真实浏览器：他人主页、两个方向的空列表");

  // 仅故障态校验拦截一次请求，其余流程均连接真实开发服务。
  const failureUrl = `**/api/users/${accounts[22].user.username}/following*`;
  await page.route(failureUrl, (route) => route.fulfill({
    status: 503, contentType: "application/json",
    body: JSON.stringify({ success: false, error: { code: "TEMPORARY_FAILURE", message: "测试网络失败" } }),
  }), { times: 1 });
  await page.getByRole("link", { name: "关注列表", exact: true }).click();
  await page.getByRole("heading", { name: "关注列表加载失败", exact: true }).waitFor();
  await page.getByRole("button", { name: "重新加载", exact: true }).click();
  await page.getByRole("heading", { name: "还没有关注任何人", exact: true }).waitFor();
  record("故障注入：加载失败不冒充空列表，重试恢复真实接口");
  assert.deepEqual(faults, []);
  await writeFile(`${output}/report.json`, JSON.stringify({ checks, faults, marker }, null, 2));
} finally {
  await browser.close();
}
