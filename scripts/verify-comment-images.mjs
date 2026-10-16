import assert from "node:assert/strict";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { createTestFetch } from "./test-api-auth.mjs";

const base = "http://localhost:8080/api";
const origin = "http://localhost:3000";
const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || "", /^\[E2E_TEST\] comment-image-\d+$/);
const stamp = marker.split("comment-image-")[1];
const request = createTestFetch(base);
const { chromium } = await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const output = "output/playwright/comment-image-live";
await mkdir(output, { recursive: true });
const checks = [];
async function api(path, method = "GET", body, token, status = 200) {
  const response = await request(base + path, {
    method,
    headers: { "Content-Type": "application/json", "X-Bypass-Rate-Limit": "true", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const result = await response.json();
  assert.equal(response.status, status, `${path}: ${JSON.stringify(result)}`);
  return result.data;
}
const account = await api("/auth/register", "POST", { username: `E2E_TEST_img_${stamp.slice(-9)}`, displayName: marker, password: "Password123!" });
const community = await api("/communities", "POST", { slug: `e2e-img-${stamp}`, name: `${marker} 图片评论`, description: marker, category: "Living" }, account.token);
const post = await api("/posts", "POST", { community: community.name, title: `${marker} 图片评论验收`, content: marker, isPublic: true }, account.token);
const png = await readFile("frontend/public/illustrations/cats/luminous-cat-reading-v2.png");
const browser = await chromium.launch({ channel: "chrome", headless: true });
const errors = [];
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await context.addInitScript(token => localStorage.setItem("luminous_token", token), account.token);
  const page = await context.newPage();
  page.on("pageerror", error => errors.push(error.message));
  await page.goto(`${origin}/post/${post.id}`);
  const textarea = page.getByLabel("发表看法", { exact: true });
  await textarea.fill(`${marker} 图文评论`);
  await textarea.evaluate((element, data) => {
    const transfer = new DataTransfer();
    transfer.items.add(new File([Uint8Array.from(atob(data), char => char.charCodeAt(0))], "[E2E_TEST] comment.png", { type: "image/png" }));
    element.dispatchEvent(new ClipboardEvent("paste", { clipboardData: transfer, bubbles: true, cancelable: true }));
  }, png.toString("base64"));
  await page.getByAltText("待发布的评论图片").waitFor();
  const responsePromise = page.waitForResponse(response => response.url().endsWith(`/api/posts/${post.id}/comments`) && response.request().method() === "POST");
  await page.getByRole("button", { name: "发表评论", exact: true }).click();
  const response = await responsePromise;
  assert.equal(response.status(), 200);
  const created = (await response.json()).data;
  assert.equal(created.content, `${marker} 图文评论`);
  const imageUrl = new URL(created.image, origin);
  assert.match(imageUrl.pathname, /^\/api\/files\//);
  assert.ok(imageUrl.searchParams.get("token"));
  await page.getByAltText("待发布的评论图片").waitFor({ state: "hidden" });
  await page.reload();
  await page.getByText(created.content, { exact: true }).waitFor();
  const displayed = page.getByRole("button", { name: "查看评论图片" }).locator("img");
  await displayed.waitFor();
  await page.waitForFunction(() => { const image = document.querySelector('button[aria-label="查看评论图片"] img'); return image?.complete && image.naturalWidth > 0; });
  assert.equal(await displayed.getAttribute("src"), created.image);
  checks.push("开发服真实浏览器粘贴、上传、图文评论提交、刷新读取、文件解码成功");

  const onlyImage = await api(`/posts/${post.id}/comments`, "POST", { image: created.image }, account.token);
  assert.equal(onlyImage.content, ""); assert.equal(onlyImage.image, created.image);
  const voted = await api(`/comments/${onlyImage.id}/vote`, "POST", { value: 1 }, account.token);
  assert.equal(voted.image, created.image);
  const loaded = await api(`/posts/${post.id}/comments`, "GET", null, account.token);
  assert.equal(loaded.length, 2);
  assert.ok(loaded.every(comment => comment.image === created.image));
  checks.push("MySQL 保存两种图片评论，重新查询及点赞响应保留图片");
  await api(`/posts/${post.id}/comments`, "POST", { content: " " }, account.token, 400);
  await api(`/posts/${post.id}/comments`, "POST", { content: marker, image: "https://example.com/not-uploaded.png" }, account.token, 400);
  assert.equal((await api(`/posts/${post.id}/comments`, "GET", null, account.token)).length, 2);
  checks.push("空评论和未上传图片返回 400，没有生成额外评论");
  await page.locator("#responses").scrollIntoViewIfNeeded();
  await page.screenshot({ path: output + "/desktop.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator("#responses").scrollIntoViewIfNeeded();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await page.screenshot({ path: output + "/mobile.png", fullPage: true });
  assert.deepEqual(errors, []);
  await writeFile(output + "/result.json", JSON.stringify({ marker, checks, errors }, null, 2));
  console.log(JSON.stringify({ checks, errors }, null, 2));
} finally { await browser.close(); }
