import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { createTestFetch } from "./test-api-auth.mjs";

const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || "", /^\[E2E_TEST\] comment-pagination-\d+$/);
const stamp = marker.split("comment-pagination-")[1];
const base = "http://localhost:8080/api", origin = "http://localhost:3000";
const request = createTestFetch(base);
const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const output = "output/playwright/comment-pagination-live", checks = [], errors = [];
await mkdir(output, { recursive: true });
async function api(path, method = "GET", body, token, status = 200, retries = 2) {
  const response = await request(base + path, { method,
    headers: { "Content-Type": "application/json", "X-Bypass-Rate-Limit": "true", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}) });
  if (response.status === 429 && status === 200 && method === "POST" && /\/posts\/[^/]+\/comments$/.test(path)) {
    assert.ok(retries > 0, "正常评论夹具的等待重试次数已用完");
    const wait = Number(response.headers.get("Retry-After"));
    assert.ok(wait > 0 && wait <= 60);
    // 普通分页夹具遵守发布速度，防刷专用验收不会自动重试限流。
    await new Promise(resolve => setTimeout(resolve, wait * 1000 + 50));
    return api(path, method, body, token, status, retries - 1);
  }
  assert.equal(response.status, status, `${method} ${path}: ${response.status}`);
  if (status === 204 || status !== 200) return;
  const result = await response.json(); assert.equal(result.success, true); return result.data;
}
const author = await api("/auth/register", "POST", { username: `E2E_TEST_cp_${stamp.slice(-9)}`, displayName: `${marker} 作者`, password: "Password123!" });
const peer = await api("/auth/register", "POST", { username: `E2E_TEST_cq_${stamp.slice(-9)}`, displayName: `${marker} 同好`, password: "Password123!" });
const community = await api("/communities", "POST", { slug: `e2e-page-${stamp}`, name: `${marker} 社区`, description: marker, category: "Living" }, author.token);
const post = await api("/posts", "POST", { community: community.name, title: "[E2E_TEST] 分页", content: marker, isPublic: true }, author.token);
const roots = [];
for (let index = 0; index < 14; index++) roots.push(await api(`/posts/${post.id}/comments`, "POST", { content: `${marker} 主评论 ${index} [猫猫开心]` }, author.token));
const top = roots.at(-1), second = roots.at(-2), orphan = roots[5];
await api(`/comments/${top.id}/vote`, "POST", { value: 1 }, peer.token);
await api(`/comments/${top.id}/vote`, "POST", { value: 1 }, author.token);
await api(`/comments/${second.id}/vote`, "POST", { value: 1 }, peer.token);
const replies = [];
for (let index = 0; index < 12; index++) replies.push(await api(`/posts/${post.id}/comments`, "POST", { content: `${marker} 楼内 ${index} [猫猫点赞]`, replyToCommentId: top.id }, peer.token));
await api(`/comments/${replies.at(-1).id}/vote`, "POST", { value: 1 }, author.token);
await api(`/posts/${post.id}/comments`, "POST", { content: `${marker} 保留回复`, replyToCommentId: orphan.id }, peer.token);
await api(`/comments/${orphan.id}`, "DELETE", null, author.token, 204);

const first = await api(`/posts/${post.id}/comment-floors?size=10`, "GET", null, author.token);
assert.equal(first.items.length, 10); assert.equal(first.hasNext, true); assert.equal(first.total, 26);
assert.equal(first.items[0].id, top.id); assert.equal(first.items[1].id, second.id);
assert.equal(first.items[0].replyCount, 12); assert.ok(!("replies" in first.items[0]));
const rest = await api(`/posts/${post.id}/comment-floors?size=10&cursor=${encodeURIComponent(first.nextCursor)}`);
assert.equal(rest.items.length, 4); assert.equal(rest.hasNext, false);
const all = [...first.items, ...rest.items];
assert.equal(new Set(all.map(item => item.id)).size, 14);
assert.ok(all.some(item => item.id === orphan.id && item.root === null && item.replyCount === 1));
const zero = all.filter(item => item.root?.votes === 0);
assert.deepEqual(zero.map(item => item.id), roots.filter(item => ![top.id,second.id,orphan.id].includes(item.id)).map(item => item.id));
let page = await api(`/posts/${post.id}/comments/${top.id}/replies?size=5`);
assert.equal(page.items.length, 5); assert.equal(page.items[0].id, replies[0].id);
assert.deepEqual(page.items.map(item => item.id), replies.slice(0, 5).map(item => item.id));
// 已加载与未加载回复同时改变票数，下一批仍必须按发表顺序完整继续。
await api(`/comments/${replies[0].id}/vote`, "POST", { value: 1 }, author.token);
await api(`/comments/${replies[7].id}/vote`, "POST", { value: 1 }, author.token);
const loaded = [...page.items];
while(page.hasNext) { page = await api(`/posts/${post.id}/comments/${top.id}/replies?size=5&cursor=${encodeURIComponent(page.nextCursor)}`); loaded.push(...page.items); }
assert.equal(new Set(loaded.map(item => item.id)).size, 12);
assert.deepEqual(loaded.map(item => item.id), replies.map(item => item.id));
await api(`/posts/${post.id}/comment-floors?size=31`, "GET", null, null, 400);
await api(`/posts/${post.id}/comment-floors?cursor=invalid`, "GET", null, null, 400);
await api(`/posts/${post.id}/comments/${top.id}/replies?cursor=${encodeURIComponent(first.nextCursor)}`, "GET", null, null, 400);
await api(`/posts/${post.id}/comments/${replies[0].id}/replies`, "GET", null, null, 404);
const hidden = await api("/posts", "POST", { community: community.name, title: "[E2E_TEST] 私密", content: marker, isPublic: false }, author.token);
await api(`/posts/${hidden.id}/comment-floors`, "GET", null, null, 404);
await api(`/posts/${hidden.id}/comments/${top.id}/replies`, "GET", null, peer.token, 404);
checks.push("真实 MySQL：主评论按票数优先，楼内仅按时间/编号；跨批投票不影响楼内顺序且无重漏；已删楼保留与分页权限校验");

const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await context.addInitScript(token => localStorage.setItem("luminous_token", token), author.token);
  const page = await context.newPage();
  page.on("pageerror", error => errors.push(error.message));
  const requests = [];
  page.on("request", req => { if (req.url().includes("/api/posts/")) requests.push(req.url()); });
  await page.goto(`${origin}/post/${post.id}`);
  await page.locator(`[data-comment-id="${top.id}"]`).waitFor();
  assert.equal(await page.locator('[data-comment-floor]').count(), 10);
  assert.equal(requests.some(url => url.includes('/replies?')), false);
  assert.equal(requests.some(url => new URL(url).pathname.endsWith('/comments')), false);
  const floor = page.locator(`[data-comment-floor="${top.id}"]`);
  await floor.getByRole("button", { name: "查看 12 条回复" }).click();
  await floor.locator(`[data-comment-id="${replies[4].id}"]`).waitFor();
  assert.equal(await floor.locator('[data-comment-id]').count(), 6);
  assert.deepEqual(await floor.locator('[aria-label="楼内讨论"] [data-comment-id]').evaluateAll(nodes => nodes.map(node => node.dataset.commentId)), replies.slice(0, 5).map(item => item.id));
  await floor.getByRole("button", { name: "查看更多回复" }).click();
  await floor.locator(`[data-comment-id="${replies[9].id}"]`).waitFor();
  assert.equal(await floor.locator('[data-comment-id]').count(), 11);
  const count = requests.filter(url => url.includes('/replies?')).length;
  await floor.getByRole("button", { name: "收起回复" }).click();
  assert.equal(await floor.locator('[aria-label="楼内讨论"]').count(), 0);
  await floor.getByRole("button", { name: "查看 12 条回复" }).click();
  assert.equal(await floor.locator('[data-comment-id]').count(), 11);
  assert.equal(requests.filter(url => url.includes('/replies?')).length, count);
  await floor.getByRole("button", { name: "查看更多回复" }).click();
  await floor.locator(`[data-comment-id="${replies[10].id}"]`).waitFor();
  assert.equal(await floor.locator('[data-comment-id]').count(), 13);
  assert.deepEqual(await floor.locator('[aria-label="楼内讨论"] [data-comment-id]').evaluateAll(nodes => nodes.map(node => node.dataset.commentId)), replies.map(item => item.id));
  assert.equal(await floor.getByRole("button", { name: "查看更多回复" }).count(), 0);
  await floor.getByRole("button", { name: "收起回复" }).click();
  await page.locator('#responses').scrollIntoViewIfNeeded();
  await page.screenshot({path: output + '/desktop.png'});
  await page.locator('button').filter({hasText:'加载更多评论'}).scrollIntoViewIfNeeded();
  await page.waitForFunction(() => document.querySelectorAll('[data-comment-floor]').length === 14);
  assert.equal(await page.locator('[data-comment-floor]').count(), 14);
  checks.push("真实浏览器：首屏只取 10 楼，不预取回复；楼内 5/5/2 分批，收起重开复用缓存；下滑追加剩余 4 楼");
  await page.setViewportSize({width:390,height:844});
  await floor.scrollIntoViewIfNeeded();
  await page.screenshot({path:output+'/mobile-collapsed.png'});
  await floor.getByRole('button',{name:'查看 12 条回复'}).click();
  await page.screenshot({path:output+'/mobile-expanded.png'});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth));
  assert.deepEqual(errors, []);
  await writeFile(output+'/result.json',JSON.stringify({checks,errors},null,2));
  console.log(JSON.stringify({checks,errors},null,2));
} finally { await browser.close(); }
