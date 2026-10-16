import { pathToFileURL } from "node:url";
const { chromium } = await import(
  pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href
);
import { readCaptchaOffset } from "./test-api-auth.mjs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import assert from "node:assert/strict";
const origin = "http://localhost:3000";
for (let attempt = 0; attempt < 40; attempt++) {
  try {
    const response = await fetch(origin + "/api/communities");
    if (response.ok) break;
  } catch {}
  if (attempt === 39) throw new Error("开发 API 启动超时");
  await new Promise((resolve) => setTimeout(resolve, 500));
}
const stamp = Date.now();
const marker = process.env.E2E_RUN_MARKER || `[E2E_TEST] ui-${stamp}`;
const username = `e2e_ui_${stamp}_a`;
const peername = `e2e_ui_${stamp}_b`;
const password = "TestPassword123!";
const slug = `e2e-ui-${stamp}`;
const title = `${marker} 手机摄影讨论`;
const out = "output/playwright/luminous-integration";
const readImageBuffer = await readFile(
  "frontend/public/illustrations/luminous-welcome.png",
);
await mkdir(out, { recursive: true });
const checks = [];
const faults = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 1024 },
  reducedMotion: "reduce",
});
const page = await context.newPage();
page.setDefaultTimeout(12000);
function record(name) {
  checks.push(name);
  console.log(`[PASS] ${name}`);
}
function observe(tab) {
  tab.on("pageerror", (error) => faults.push(error.message));
  tab.on("response", async (response) => {
    if (
      new URL(response.url()).pathname === "/api/auth/captcha" &&
      response.ok()
    )
      tab.challenge = (await response.json()).data;
  });
}
observe(page);
async function action(tab, path, method, click) {
  const pending = tab.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api${path}` &&
      response.request().method() === method,
  );
  await click();
  const response = await pending;
  if (response.status() === 204) return {};
  const payload = await response.json();
  assert(
    response.ok() && payload.success,
    `${method} ${path}: ${JSON.stringify(payload.error)}`,
  );
  return payload.data;
}
async function solveCaptcha(tab) {
  await tab
    .getByRole("button", { name: "拖动安全验证滑块" })
    .waitFor({ state: "visible" });
  await tab.waitForFunction(
    () => !!document.querySelector('img[alt="待对齐拼图"]'),
  );
  const challenge = tab.challenge;
  assert(challenge?.challengeId, "浏览器收到真实验证码挑战");
  const session = (await tab.context().cookies()).find(
    (cookie) => cookie.name === "luminous_captcha_session",
  )?.value;
  const offset = await readCaptchaOffset(session, challenge.challengeId);
  const slider = tab.getByRole("button", { name: "拖动安全验证滑块" });
  const box = await slider.boundingBox();
  const width = await slider.evaluate(
    (button) => button.parentElement.clientWidth,
  );
  await tab.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await tab.mouse.down();
  await tab.mouse.move(
    box.x +
      box.width / 2 +
      (offset * (width - 44)) / (challenge.width - challenge.pieceSize),
    box.y + box.height / 2,
    { steps: 16 },
  );
  await tab.waitForTimeout(350);
  await tab.mouse.up();
  await tab.getByText("安全验证通过", { exact: true }).waitFor();
}
async function register(tab, name, display) {
  await tab.goto(origin);
  await tab.getByRole("button", { name: "登录", exact: true }).first().click();
  await tab.getByRole("button", { name: /立即注册/ }).click();
  const dialog = tab.getByRole("dialog");
  await dialog.getByLabel("用户名", { exact: true }).fill(name);
  await dialog.getByLabel("昵称", { exact: true }).fill(display);
  await dialog.getByLabel("密码", { exact: true }).fill(password);
  await dialog.getByRole("button", { name: "下一步：选择头像", exact: true }).click();
  await solveCaptcha(tab);
  const user = await action(tab, "/auth/register", "POST", () =>
    dialog.getByRole("button", { name: /完成注册/ }).click(),
  );
  await tab.getByRole("button", { name: "打开用户菜单" }).waitFor();
  return user.user;
}
async function readApi(tab, path) {
  const response = await tab.request.get(`${origin}/api${path}`, {
    headers: {
      Authorization: `Bearer ${await tab.evaluate(() => localStorage.getItem("luminous_token"))}`,
    },
  });
  const payload = await response.json();
  assert(
    response.ok() && payload.success,
    `${path}: ${JSON.stringify(payload.error)}`,
  );
  return payload.data;
}
let peerContext;
try {
  await page.goto(origin);
  await page.locator(".feed-panel").waitFor();
  record("真实首页加载、空状态与 Nginx API 代理");
  const me = await register(page, username, `${marker} 作者`);
  assert.equal(me.growth.experience, 0);
  assert.equal(me.growth.level, 1);
  record("浏览器注册、正式验证码拖动与自动登录");
  await page.goto(`${origin}/u/${username}`);
  await page.getByRole("button", { name: "编辑资料", exact: true }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel("个人简介").fill(`${marker} 喜欢摄影和读书`);
  await dialog.getByLabel("头像图片文件").setInputFiles({
    name: `${marker}.txt`, mimeType: "text/plain", buffer: Buffer.from(marker),
  });
  await dialog.getByRole("alert").getByText("请选择 JPG、PNG、GIF 或 WebP 图片。").waitFor();
  await dialog.getByLabel("头像图片文件").setInputFiles({
    name: `${marker}-large.png`, mimeType: "image/png", buffer: Buffer.alloc(10 * 1024 * 1024 + 1),
  });
  await dialog.getByRole("alert").getByText("图片不能超过 10 MB，请选择更小的图片。").waitFor();
  const chooserPending = page.waitForEvent("filechooser");
  await dialog.getByRole("button", { name: "选择头像", exact: true }).click();
  await (await chooserPending).setFiles({
    name: `${marker}-avatar.png`, mimeType: "image/png", buffer: readImageBuffer,
  });
  await dialog.getByLabel("封面图片文件").setInputFiles({
    name: `${marker}-banner.png`, mimeType: "image/png", buffer: readImageBuffer,
  });
  await page.waitForFunction(() => {
    const images = [...document.querySelectorAll('[role="dialog"] img')];
    return images.length === 2 && images.every(image => image.complete && image.naturalWidth > 0);
  });
  await page.screenshot({ path: `${out}/profile-image-picker-desktop.png` });
  await page.setViewportSize({ width: 390, height: 844 });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  await dialog.getByRole("button", { name: "保存修改" }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: `${out}/profile-image-picker-mobile.png` });
  await page.setViewportSize({ width: 1440, height: 1024 });
  record("头像原生文件选择器、格式与大小校验、本地预览和手机布局");
  const rejectedUpload = async route => route.fulfill({
    status: 400, contentType: "application/json",
    body: JSON.stringify({ success: false, error: { code: "INVALID_FILE_TYPE", message: "测试图片上传失败，请重试" } }),
  });
  await page.route("**/api/uploads/images", rejectedUpload);
  const rejectedResponse = page.waitForResponse(response => response.url().endsWith("/api/uploads/images"));
  await dialog.getByRole("button", { name: "保存修改" }).click();
  assert.equal((await rejectedResponse).status(), 400);
  await page.getByText("测试图片上传失败，请重试", { exact: true }).waitFor();
  assert(await dialog.isVisible());
  assert.equal(await dialog.getByAltText("头像预览").count(), 1);
  await page.unroute("**/api/uploads/images", rejectedUpload);
  record("上传失败保留编辑内容与图片预览，允许重新保存");
  const updatedProfile = await action(page, "/auth/me", "PUT", () =>
    dialog.getByRole("button", { name: "保存修改" }).click(),
  );
  assert(updatedProfile.avatar && updatedProfile.banner);
  const persistedProfile = await readApi(page, "/auth/me");
  assert.equal(persistedProfile.avatar, updatedProfile.avatar);
  assert.equal(persistedProfile.banner, updatedProfile.banner);
  await page.getByText(`${marker} 喜欢摄影和读书`, { exact: true }).waitFor();
  await page.reload();
  await page.getByRole("button", { name: "编辑资料", exact: true }).click();
  dialog = page.getByRole("dialog");
  await page.waitForFunction(() => [...document.querySelectorAll('[role="dialog"] img')].every(image => image.complete && image.naturalWidth > 0));
  assert.equal(await dialog.getByAltText("头像预览").getAttribute("src"), updatedProfile.avatar);
  assert.equal(await dialog.getByAltText("封面预览").getAttribute("src"), updatedProfile.banner);
  await dialog.getByRole("button", { name: "取消", exact: true }).click();
  record("真实头像与封面上传、资料持久化、刷新后读取图片");
  await page.goto(`${origin}/salons`);
  await page.getByRole("button", { name: "创建社区", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("社区短名称", { exact: true }).fill(slug);
  await dialog
    .getByLabel("社区名称", { exact: true })
    .fill(`${marker} 光影日常`);
  await dialog
    .getByLabel("社区简介", { exact: true })
    .fill(`${marker} 用手机分享日常照片`);
  await action(page, "/communities", "POST", () =>
    dialog.getByRole("button", { name: "创建社区", exact: true }).click(),
  );
  await page
    .getByRole("heading", { name: `${marker} 光影日常`, exact: true })
    .waitFor();
  record("创建社区、跳转详情、创建者成员身份");
  await page.getByRole("button", { name: "管理", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("社区简介").fill(`${marker} 欢迎分享手机摄影作品`);
  await dialog.getByLabel("社区图标图片文件").setInputFiles({
    name: `${marker}-icon.png`, mimeType: "image/png", buffer: readImageBuffer,
  });
  await dialog.getByLabel("社区封面图片文件").setInputFiles({
    name: `${marker}-cover.png`, mimeType: "image/png", buffer: readImageBuffer,
  });
  await dialog
    .getByLabel("社区规则（每行一条）")
    .fill(`${marker} 友善交流\n${marker} 分享原创内容`);
  const updatedCommunity = await action(page, `/communities/${slug}`, "PUT", () =>
    dialog.getByRole("button", { name: "保存配置" }).click(),
  );
  assert(updatedCommunity.icon && updatedCommunity.banner);
  await page
    .getByText(`${marker} 欢迎分享手机摄影作品`, { exact: true })
    .waitFor();
  record("创建者编辑社区资料和讨论规则");
  const checkin = await action(page, `/checkin/${slug}`, "POST", () =>
    page.getByRole("button", { name: "签到，和大家打个招呼" }).click(),
  );
  assert(checkin.totalDays === 1);
  assert(await page.getByRole("button", { name: "今日已签到" }).isDisabled());
  record("真实社区签到与已签到按钮状态");
  await page.getByRole("button", { name: "发帖", exact: true }).last().click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("标题", { exact: true }).fill(title);
  await dialog
    .getByLabel("正文", { exact: true })
    .fill(`${marker} **今天的光线很好**，一起聊聊构图。`);
  const upload = await action(page, "/uploads/images", "POST", () =>
    dialog
      .getByLabel("添加帖子图片")
      .setInputFiles({
        name: `${marker}.png`,
        mimeType: "image/png",
        buffer: readImageBuffer,
      }),
  );
  assert(new URL(upload.url).pathname.startsWith("/api/files/"));
  const fileUrl = new URL(upload.url);
  const sameOriginFile = await page.request.get(
    origin + fileUrl.pathname + fileUrl.search,
  );
  assert(
    sameOriginFile.ok() &&
      sameOriginFile.headers()["content-type"] === "image/png",
  );
  assert.deepEqual(await sameOriginFile.body(), readImageBuffer);
  await page.waitForFunction(() => {
    const img = document.querySelector('[role="dialog"] img');
    return img && img.complete && img.naturalWidth > 0;
  });
  record("真实 MinIO 2 MB 图片上传、同源文件接口与浏览器预览");
  const post = await action(page, "/posts", "POST", () =>
    dialog.getByRole("button", { name: "发布帖子", exact: true }).click(),
  );
  assert(post.community.name === slug);
  assert.equal(post.author.personalGrowth.experience, 20);
  assert.equal(post.author.communityGrowth.experience, 20);
  assert.equal(post.author.communityGrowth.level, 3);
  const growth = await readApi(page, `/users/${username}/growth?community=${slug}`);
  assert.equal(growth.personal.experience, 20);
  assert.equal(growth.community.experience, 20);
  assert.equal(growth.personal.experienceToNextLevel, 10);
  await page.locator('[aria-label="社区内等级 3 级"]').waitFor();
  record("签到和发帖返回真实双维度等级，社区作者徽章与经验进度一致");
  await page.getByRole("link", { name: title, exact: true }).waitFor();
  await page.screenshot({ path: `${out}/community.png`, fullPage: true });
  record("真实发帖、社区列表刷新与图片关联");
  const card = page
    .locator("article")
    .filter({ has: page.getByRole("link", { name: title, exact: true }) });
  for (const value of [1, 0, 1]) {
    const updated = await action(page, `/posts/${post.id}/vote`, "POST", () =>
      card.getByRole("button", { name: `赞同 ${title}` }).click(),
    );
    assert.equal(updated.userVote, value);
    assert.equal(updated.votes, value);
    assert.equal(
      await card
        .getByRole("button", { name: `赞同 ${title}` })
        .getAttribute("aria-pressed"),
      String(value === 1),
    );
  }
  record("帖子点赞、取消、再次点赞的完整前端状态转换");
  await action(page, `/posts/${post.id}/bookmark`, "POST", () =>
    card.getByRole("button", { name: "收藏", exact: true }).click(),
  );
  await page.goto(`${origin}/archive`);
  await page.getByRole("link", { name: title, exact: true }).waitFor();
  await action(page, `/posts/${post.id}/bookmark`, "DELETE", () =>
    page.getByRole("button", { name: "已收藏", exact: true }).click(),
  );
  assert.equal((await readApi(page, "/bookmarks")).total, 0);
  record("收藏进入收藏页、取消收藏与真实持久化结果");
  await page.goto(`${origin}/post/${post.id}`);
  await page.getByLabel("发表看法").fill(`${marker} 留言：这张图很有趣`);
  const comment = await action(page, `/posts/${post.id}/comments`, "POST", () =>
    page.getByRole("button", { name: "发表评论", exact: true }).click(),
  );
  const commentArticle = page
    .locator("#responses article")
    .filter({ has: page.getByText(comment.content, { exact: true }) });
  for (const value of [1, 0, 1]) {
    const updated = await action(
      page,
      `/comments/${comment.id}/vote`,
      "POST",
      () => commentArticle.locator(".post-action").first().click(),
    );
    assert.equal(updated.userVote, value);
    assert.equal(updated.votes, value);
  }
  await page.reload();
  await page.getByText(comment.content, { exact: true }).waitFor();
  record("评论发布、点赞三态转换与刷新持久化");
  await page
    .getByRole("combobox", { name: "搜索帖子、社区、用户" })
    .fill(`手机摄影讨论`);
  await page
    .getByRole("option", { name: new RegExp("手机摄影讨论") })
    .first()
    .click();
  await page.getByRole("heading", { name: title, exact: true }).waitFor();
  record("真实全文搜索与结果跳转");
  peerContext = await browser.newContext({
    viewport: { width: 1440, height: 1024 },
  });
  const peerPage = await peerContext.newPage();
  peerPage.setDefaultTimeout(12000);
  observe(peerPage);
  await register(peerPage, peername, `${marker} 同好`);
  await peerPage.goto(`${origin}/s/${slug}`);
  for (const [method, joined] of [
    ["POST", true],
    ["DELETE", false],
    ["POST", true],
  ]) {
    const community = await action(
      peerPage,
      `/communities/${slug}/join`,
      method,
      () =>
        peerPage
          .getByRole("button", {
            name: joined ? "加入社区" : "已加入 · 退出",
            exact: true,
          })
          .click(),
    );
    assert.equal(community.joined, joined);
  }
  record("社区加入、退出、重新加入与成员状态");
  await peerPage.goto(`${origin}/u/${username}`);
  for (const [method, following] of [
    ["POST", true],
    ["DELETE", false],
    ["POST", true],
  ]) {
    const profile = await action(
      peerPage,
      `/users/${username}/follow`,
      method,
      () =>
        peerPage
          .getByRole("button", {
            name: following ? "关注" : "已关注",
            exact: true,
          })
          .click(),
    );
    assert.equal(profile.isFollowing, following);
  }
  await peerPage.goto(origin);
  await peerPage.getByRole("tab", { name: "关注", exact: true }).click();
  await peerPage.getByRole("link", { name: title, exact: true }).waitFor();
  record("真实关注、取消、重新关注与关注动态流");
  await peerPage.goto(`${origin}/post/${post.id}`);
  await peerPage.getByLabel("发表看法").fill(`${marker} 同好留言`);
  await action(peerPage, `/posts/${post.id}/comments`, "POST", () =>
    peerPage.getByRole("button", { name: "发表评论" }).click(),
  );
  for (let i = 0; i < 15; i++) {
    if (
      (await readApi(page, "/notifications")).items.some(
        (n) => n.type === "COMMENT_POST",
      )
    )
      break;
    await page.waitForTimeout(1000);
  }
  await page.getByRole("button", { name: /^通知/ }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByText(`${marker} 同好留言`, { exact: true }).waitFor();
  assert((await dialog.innerText()).includes("评论了你的帖子"));
  assert((await dialog.innerText()).includes("关注了你"));
  assert(!(await dialog.innerText()).includes("8 小时前"));
  await action(page, "/notifications/read-all", "PUT", () =>
    dialog.getByRole("button", { name: "全部已读" }).click(),
  );
  assert.equal(
    (await readApi(page, "/notifications/unread-count")).unreadCount,
    0,
  );
  await page.screenshot({ path: `${out}/notifications.png`, fullPage: true });
  await page.keyboard.press("Escape");
  record("RocketMQ 关注和评论通知、全部已读与未读计数同步");
  await page.getByRole("button", { name: "打开用户菜单" }).click();
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await page.getByRole("button", { name: "登录", exact: true }).first().click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("用户名", { exact: true }).fill(username);
  await dialog.getByLabel("密码", { exact: true }).fill(password);
  await solveCaptcha(page);
  await action(page, "/auth/login", "POST", () =>
    dialog.getByRole("button", { name: "立即登录" }).click(),
  );
  await page.getByRole("button", { name: "打开用户菜单" }).waitFor();
  record("退出后正式验证码登录与账号状态恢复");
  await page.goto(origin);
  await page.getByRole("link", { name: title, exact: true }).waitFor();
  await page.screenshot({ path: `${out}/home-desktop.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${origin}/post/${post.id}`);
  await page.getByRole("heading", { name: title, exact: true }).waitFor();
  assert(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.screenshot({ path: `${out}/post-mobile.png`, fullPage: true });
  record("手机详情、真实内容与无横向溢出");
  await page.setViewportSize({ width: 1440, height: 1024 });
  await page.goto(`${origin}/u/${username}`);
  await page
    .getByRole("heading", { name: me.displayName, exact: true })
    .waitFor();
  assert.equal((await readApi(page, `/users/${username}`)).stats.posts, 1);
  await page.locator('[aria-label="个人等级 3 级"]').waitFor();
  await page.getByText("20 经验", { exact: true }).waitFor();
  await page.getByText("距下一级 10 经验", { exact: true }).waitFor();
  const ledger = await readApi(page, "/growth/ledger");
  assert.equal(ledger.items.reduce((sum, entry) => sum + entry.delta, 0), 20);
  await page.screenshot({ path: `${out}/growth-profile-desktop.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  await page.screenshot({ path: `${out}/growth-profile-mobile.png`, fullPage: true });
  record("个人主页真实等级与升级差额、自己的奖励流水、手机徽章布局");
  record("个人主页帖子统计与真实作者帖子查询");
  assert.deepEqual(faults, []);
  record("全流程无浏览器运行时异常");
  await writeFile(
    `${out}/report.json`,
    JSON.stringify(
      {
        marker,
        origin,
        checks,
        passed: checks.length,
        faults,
        postId: post.id,
        finishedAt: new Date().toISOString(),
      },
      null,
      2,
    ),
  );
  console.log(
    `[COMPLETE] ${checks.length} 项真实浏览器验收通过；批次 ${marker}`,
  );
} catch (error) {
  await page
    .screenshot({ path: `${out}/failure.png`, fullPage: true })
    .catch(() => {});
  await writeFile(
    `${out}/failure.txt`,
    `${error.stack}\n\n${await page.locator("body").innerText()}`,
  );
  throw error;
} finally {
  await browser.close();
}
