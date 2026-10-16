import assert from "node:assert/strict";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { createTestFetch } from "./test-api-auth.mjs";

const base = "http://localhost:8080/api", origin = "http://localhost:3000";
const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || "", /^\[E2E_TEST\] comment-replies-\d+$/);
const stamp = marker.split("comment-replies-")[1];
const request = createTestFetch(base);
const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const output = "output/playwright/comment-replies-live", checks = [], errors = [];
await mkdir(output, { recursive: true });
async function api(path, method = "GET", body, token, status = 200) {
  const response = await request(base + path, {
    method,
    headers: { "Content-Type": "application/json", "X-Bypass-Rate-Limit": "true", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (status === 204) { assert.equal(response.status, status); return; }
  const result = await response.json();
  assert.equal(response.status, status, `${path}: ${JSON.stringify(result)}`);
  return result.data;
}
const author = await api("/auth/register", "POST", { username: `E2E_TEST_root_${stamp.slice(-9)}`, displayName: `${marker} 作者`, password: "Password123!" });
const peer = await api("/auth/register", "POST", { username: `E2E_TEST_peer_${stamp.slice(-9)}`, displayName: `${marker} 同好`, password: "Password123!" });
const community = await api("/communities", "POST", { slug: `e2e-reply-${stamp}`, name: `${marker} 社区`, description: marker, category: "Living" }, author.token);
const post = await api("/posts", "POST", { community: community.name, title: `${marker} 楼中楼`, content: marker, isPublic: true }, author.token);
const other = await api("/posts", "POST", { community: community.name, title: `${marker} 另一帖子`, content: marker, isPublic: true }, author.token);
const png = await readFile("frontend/public/illustrations/cats/luminous-cat-reading-v2.png");
const form = new FormData();
form.append("file", new Blob([png], { type: "image/png" }), "[E2E_TEST] root.png");
const upload = await request(base + "/uploads/images", { method: "POST", headers: { Authorization: `Bearer ${author.token}`, "X-Bypass-Rate-Limit": "true" }, body: form });
assert.equal(upload.status, 200);
const image = (await upload.json()).data.url;
const root = await api(`/posts/${post.id}/comments`, "POST", { content: `${marker} 主评论`, image }, author.token);
assert.equal(root.parentId, null);
const browser = await chromium.launch({ channel: "chrome", headless: true });
async function pageFor(account) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await context.addInitScript(token => localStorage.setItem("luminous_token", token), account.token);
  const page = await context.newPage();
  page.on("pageerror", error => errors.push(error.message));
  await page.goto(`${origin}/post/${post.id}`);
  await page.locator(`[data-comment-id="${root.id}"]`).waitFor();
  return page;
}
try {
  const peerPage = await pageFor(peer);
  const floor = peerPage.locator(`[data-comment-floor="${root.id}"]`);
  await floor.locator(`[data-comment-id="${root.id}"]`).getByRole("button", { name: "回复", exact: true }).click();
  const composer = floor.getByRole("form", { name: "楼内回复" });
  assert.equal(await composer.getByRole("textbox").inputValue(), "");
  assert.equal(await composer.locator('input[type="file"]').count(), 0);
  await composer.getByRole("textbox").fill(`${marker} 回复主评论`);
  let responsePromise = peerPage.waitForResponse(response => response.url().endsWith(`/api/posts/${post.id}/comments`) && response.request().method() === "POST");
  await composer.getByRole("button", { name: "发表回复" }).click();
  let response = await responsePromise;
  assert.equal(response.status(), 200);
  const child = (await response.json()).data;
  assert.equal(child.parentId, root.id);
  assert.equal(child.replyToCommentId, root.id);
  assert.equal(child.image, null);
  assert.ok(!child.content.startsWith("@"));
  await composer.waitFor({ state: "hidden" });
  checks.push("真实浏览器直接回复主评论，不填写 @，楼内无图片入口");

  const authorPage = await pageFor(author);
  const authorFloor = authorPage.locator(`[data-comment-floor="${root.id}"]`);
  await authorFloor.locator(`[data-comment-id="${child.id}"]`).getByRole("button", { name: "回复", exact: true }).click();
  const authorComposer = authorFloor.getByRole("form", { name: "楼内回复" });
  await authorComposer.getByRole("textbox").fill(`${marker} 回复楼内同好`);
  responsePromise = authorPage.waitForResponse(response => response.url().endsWith(`/api/posts/${post.id}/comments`) && response.request().method() === "POST");
  await authorComposer.getByRole("button", { name: "发表回复" }).click();
  response = await responsePromise;
  assert.equal(response.status(), 200);
  const nested = (await response.json()).data;
  assert.equal(nested.parentId, root.id);
  assert.equal(nested.replyToCommentId, child.id);
  assert.equal(nested.replyTo.username, peer.user.username);
  const continued = await api(`/posts/${post.id}/comments`, "POST", { content: `${marker} 继续交流`, replyToCommentId: nested.id }, peer.token);
  assert.equal(continued.parentId, root.id);
  const voted = await api(`/comments/${nested.id}/vote`, "POST", { value: 1 }, peer.token);
  assert.equal(voted.parentId, root.id); assert.equal(voted.replyToCommentId, child.id);
  await authorPage.reload();
  await authorFloor.locator(`[data-comment-id="${nested.id}"]`).getByRole("link", { name: `@${peer.user.displayName}`, exact: true }).waitFor();
  assert.equal(await authorFloor.locator('[aria-label="楼内讨论"]').count(), 1);
  checks.push("回复楼内同好正确标记 @，多次继续回复只保留一层，刷新和点赞保留关系");

  await api(`/posts/${post.id}/comments`, "POST", { content: marker, image, replyToCommentId: root.id }, author.token, 400);
  await api(`/posts/${post.id}/comments`, "POST", { image, replyToCommentId: root.id }, author.token, 400);
  await api(`/posts/${post.id}/comments`, "POST", { content: " ", replyToCommentId: root.id }, author.token, 400);
  await api(`/posts/${other.id}/comments`, "POST", { content: marker, replyToCommentId: root.id }, author.token, 404);
  assert.equal((await api(`/posts/${post.id}/comments`, "GET", null, author.token)).length, 4);
  checks.push("有效已上传图片仍不能用于楼中楼，纯图片/空回复/跨帖回复被拒绝，没有额外写入");

  let notice;
  for (let attempt = 0; attempt < 15; attempt++) {
    const notices = await api("/notifications?type=COMMENT", "GET", null, peer.token);
    notice = notices.items.find(item => item.type === "REPLY_COMMENT" && item.actor.username === author.user.username && item.target?.id === post.id);
    if (notice) break;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  assert.ok(notice, "被回复的楼内用户应收到 REPLY_COMMENT 通知");
  checks.push("实际 MQ 通知链路将楼内回复送给被回复的人");
  await authorPage.locator("#responses").screenshot({ path: output + "/desktop.png" });
  await authorPage.setViewportSize({ width: 390, height: 844 });
  assert.equal(await authorPage.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await authorPage.locator("#responses").screenshot({ path: output + "/mobile.png" });

  await api(`/comments/${root.id}`, "DELETE", null, author.token, 204);
  await api(`/posts/${post.id}/comments`, "POST", { content: marker, replyToCommentId: root.id }, peer.token, 404);
  await authorPage.reload();
  await authorFloor.getByText("原评论已删除，楼内回复已保留。").waitFor();
  assert.equal(await authorFloor.locator(`[data-comment-id="${child.id}"]`).count(), 1);
  await api(`/comments/${child.id}`, "DELETE", null, peer.token, 204);
  await api(`/posts/${post.id}/comments`, "POST", { content: marker, replyToCommentId: child.id }, author.token, 404);
  const surviving = await api(`/posts/${post.id}/comments`, "GET", null, author.token);
  assert.equal(surviving.find(comment => comment.id === nested.id).replyTo.username, peer.user.username);
  const afterDelete = await api(`/posts/${post.id}/comments`, "POST", { content: `${marker} 原主评论删除后继续讨论`, replyToCommentId: nested.id }, peer.token);
  assert.equal(afterDelete.parentId, root.id);
  assert.equal((await api(`/posts/${post.id}`, "GET", null, author.token)).comments, 3);
  await authorPage.reload();
  await authorFloor.locator(`[data-comment-id="${afterDelete.id}"]`).waitFor();
  assert.equal(await authorFloor.locator('[aria-label="楼内讨论"]').count(), 1);
  checks.push("原评论和回复删除不级联，历史 @ 保留，删除对象不能新回复，讨论可继续且评论总数一致");
  assert.deepEqual(errors, []);
  await writeFile(output + "/result.json", JSON.stringify({ marker, checks, errors }, null, 2));
  console.log(JSON.stringify({ checks, errors }, null, 2));
} finally { await browser.close(); }
