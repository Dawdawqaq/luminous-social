import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { chromium } from "file:///D:/gongzuo/luminous/.local/npm-cache/_npx/31e32ef8478fbf80/node_modules/playwright/index.mjs";

const directory = fileURLToPath(new URL(".", import.meta.url));
const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1200, height: 1200 }, deviceScaleFactor: 1 });
  await page.route("https://luminous-preview.test/**", async route => {
    const name = new URL(route.request().url()).pathname.slice(1);
    if (!/^(preview\.html|happy\.png|like\.png|laugh\.png|confused\.png|sad\.png|love\.png)$/.test(name)) return route.abort();
    return route.fulfill({ body: await readFile(directory + name), contentType: name.endsWith(".png") ? "image/png" : "text/html; charset=utf-8" });
  });
  await page.goto("https://luminous-preview.test/preview.html");
  await page.locator(".art").evaluateAll(images => Promise.all(images.map(image => image.decode())));
  const assets = await page.locator(".art").evaluateAll(images => images.map(image => {
    const canvas = document.createElement("canvas");
    canvas.width = image.naturalWidth; canvas.height = image.naturalHeight;
    const context = canvas.getContext("2d");
    context.drawImage(image, 0, 0);
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    let transparent = 0, visible = 0;
    for (let index = 3; index < pixels.length; index += 4) {
      if (pixels[index] === 0) transparent++;
      if (pixels[index] > 0) visible++;
    }
    // 取脸部中段白色毛面的最大跨度，排除耳朵、胡须和下方道具对尺寸比较的影响。
    let headWidth = 0;
    for (let y = Math.floor(canvas.height * .5); y <= Math.floor(canvas.height * .62); y++) {
      let first = canvas.width, last = -1;
      for (let x = 0; x < canvas.width; x++) {
        const index = (y * canvas.width + x) * 4;
        if (pixels[index] > 230 && pixels[index + 1] > 230 && pixels[index + 2] > 230 && pixels[index + 3] > 200) {
          first = Math.min(first, x); last = x;
        }
      }
      if (last >= first) headWidth = Math.max(headWidth, last - first + 1);
    }
    return { name: image.getAttribute("src"), width: canvas.width, height: canvas.height, transparentPixels: transparent, visiblePixels: visible, headWidthFraction: headWidth / canvas.width };
  }));
  if (assets.some(asset => !asset.transparentPixels || !asset.visiblePixels)) throw new Error("表情图片缺少透明背景或有效内容");
  await page.screenshot({ path: directory + "contact-sheet.png", fullPage: true });
  await page.getByRole("button", { name: "切换深色背景" }).click();
  await page.screenshot({ path: directory + "contact-sheet-dark.png", fullPage: true });
  await page.getByRole("button", { name: "切换浅色背景" }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error("预览在手机上横向溢出");
  await page.screenshot({ path: directory + "preview-mobile.png", fullPage: true });
  await writeFile(directory + "validation.json", JSON.stringify({ assets, mobileWidth: 390 }, null, 2));
  console.log(JSON.stringify({ assets }, null, 2));
} finally { await browser.close(); }
