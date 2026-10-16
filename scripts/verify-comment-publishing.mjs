import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { createTestFetch } from "./test-api-auth.mjs";

const marker = process.env.E2E_RUN_MARKER;
assert.match(marker || "", /^\[E2E_TEST\] comment-publishing-\d+$/);
const stamp = marker.split("comment-publishing-")[1];
const base = "http://localhost:8080/api", origin = "http://localhost:3000";
const request = createTestFetch(base), checks = [], errors = [];
const output = "output/playwright/comment-publishing-live";
await mkdir(output, {recursive:true});
async function api(path, method="GET", body, token, key=randomUUID()) {
  const response = await request(base + path, {method,
    headers: {"Content-Type":"application/json", "X-Bypass-Rate-Limit":"true", "X-Idempotency-Key":key, ...(token ? {Authorization:`Bearer ${token}`} : {})},
    ...(body ? {body:JSON.stringify(body)} : {})});
  return {status:response.status, data:response.status === 204 ? null : (await response.json()).data,
    wait:Number(response.headers.get("Retry-After")), replay:response.headers.get("Idempotency-Replayed")};
}
async function ok(path,method,body,token) { const result=await api(path,method,body,token); assert.equal(result.status,200,path); return result.data; }
async function account(label) { return ok('/auth/register','POST',{username:`E2E_TEST_${label}_${stamp.slice(-9)}`,displayName:`${marker} ${label}`,password:'Password123!'}); }
const owner=await account('owner'), actor=await account('actor'), racer=await account('racer'), limited=await account('minute'), invalid=await account('invalid');
const community=await ok('/communities','POST',{slug:`e2e-publish-${stamp}`,name:marker,description:marker,category:'Living'},owner.token);
const post=await ok('/posts','POST',{community:community.name,title:'[E2E_TEST] 发布',content:marker,isPublic:true},owner.token);
const other=await ok('/posts','POST',{community:community.name,title:'[E2E_TEST] 并发',content:marker,isPublic:true},owner.token);
const root=await ok(`/posts/${post.id}/comments`,'POST',{content:`${marker} 原有热评`},owner.token);
await ok(`/comments/${root.id}/vote`,'POST',{value:1},actor.token);

const publishPath=`/posts/${post.id}/comments`;
const bad=await api(publishPath,'POST',{content:`${marker} 失败`,replyToCommentId:'9223372036854775807'},invalid.token);
assert.equal(bad.status,404);
const valid=await api(publishPath,'POST',{content:`${marker} 失败后立即成功`},invalid.token);
assert.equal(valid.status,200);
const shared=await api(publishPath,'POST',{content:`${marker} 楼内共用`,replyToCommentId:root.id},invalid.token);
assert.equal(shared.status,429); assert.ok(shared.wait>0 && shared.wait<=5);
checks.push('真实失败不消耗成功额度；主评论与楼内回复共用五秒冷却及 Retry-After');

const concurrent=await Promise.all([api(publishPath,'POST',{content:`${marker} 并发甲`},racer.token),api(`/posts/${other.id}/comments`,'POST',{content:`${marker} 并发乙`},racer.token)]);
assert.deepEqual(concurrent.map(result=>result.status).sort(),[200,429]);
const created=concurrent.find(result=>result.status===200).data;
const removed=await api(`/comments/${created.id}`,'DELETE',null,racer.token);
assert.equal(removed.status,204);
assert.equal((await api(publishPath,'POST',{content:`${marker} 删后重发`},racer.token)).status,429);
checks.push('同用户不同帖并发只有一条成功；删除评论不退还额度或冷却');

// 只在已核对的独立开发库中，为本批用户构造一分钟十次成功发布的时间窗口。
const userId=String(limited.user.id); assert.match(userId,/^\d+$/);
const docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
const seedId=7000000000000000000n + (BigInt(stamp)%100000n)*100n;
let sql=`INSERT INTO comment_publish_gates(user_id,last_published_at) VALUES(${userId},CURRENT_TIMESTAMP(6)-INTERVAL 10 SECOND);\n`;
for(let index=0;index<10;index++) sql+=`INSERT INTO comment_publications(comment_id,user_id,published_at) VALUES(${seedId+BigInt(index)},${userId},CURRENT_TIMESTAMP(6)-INTERVAL ${55-index*5} SECOND);\n`;
execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:sql,encoding:'utf8'});
const minute=await api(publishPath,'POST',{content:`${marker} 分钟限额`},limited.token);
assert.equal(minute.status,429); assert.ok(minute.wait>0 && minute.wait<=5);
checks.push('真实 MySQL 滚动一分钟十条额度在最小间隔已满足时仍然生效');

const {chromium}=await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({channel:'chrome',headless:true});
try {
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  await context.addInitScript(token=>localStorage.setItem('luminous_token',token),actor.token);
  const page=await context.newPage(); page.on('pageerror',error=>errors.push(error.message));
  let loseResponse=true, firstKey, publishedId, sent=0;
  await page.route(`**/api/posts/${post.id}/comments`,async route=>{
    if(route.request().method()!=='POST') return route.continue();
    sent++;
    if(loseResponse) {
      firstKey=route.request().headers()['x-idempotency-key'];
      const response=await route.fetch(); assert.equal(response.status(),200);
      publishedId=(await response.json()).data.id; loseResponse=false;
      await route.abort('failed');
    } else {
      if(sent===2) assert.equal(route.request().headers()['x-idempotency-key'],firstKey);
      await route.continue();
    }
  });
  await page.goto(`${origin}/post/${post.id}`);
  await page.locator(`[data-comment-id="${root.id}"]`).waitFor();
  const form=page.getByRole('form',{name:'发布评论',exact:true}), input=form.getByRole('textbox');
  const text=`${marker} 我的新评论 [猫猫开心]`;
  await input.fill(text);
  await form.getByRole('button',{name:'发表评论',exact:true}).click();
  await form.getByText('网络连接中断，请重试。',{exact:true}).waitFor();
  assert.equal(await input.textContent(),text);
  const retry=page.waitForResponse(response=>response.url().endsWith(`/posts/${post.id}/comments`) && response.request().method()==='POST');
  await form.getByRole('button',{name:'发表评论',exact:true}).click();
  const replayed=await retry; assert.equal(replayed.headers()['idempotency-replayed'],'true');
  await page.locator(`[data-comment-id="${publishedId}"]`).waitFor();
  assert.equal(await page.locator('[data-comment-floor]').first().getAttribute('data-comment-floor'),publishedId);
  assert.equal(await page.locator(`[data-comment-id="${publishedId}"]`).count(),1);
  assert.equal((await ok(`/posts/${post.id}/comments`,'GET',null,actor.token)).filter(comment=>comment.id===publishedId).length,1);
  await input.fill(`${marker} 下一条草稿`);
  assert.equal(await form.getByRole('button',{name:/秒后可发布/}).isDisabled(),true);
  while(await page.getByRole('button',{name:'关闭提示',exact:true}).count()) await page.getByRole('button',{name:'关闭提示',exact:true}).first().click();
  await page.evaluate(()=>{document.activeElement?.blur(); window.scrollTo({top:document.getElementById('responses').getBoundingClientRect().top+scrollY-115,behavior:'instant'});});
  await page.screenshot({path:output+'/desktop-pinned.png'});
  await page.setViewportSize({width:390,height:844});
  await page.evaluate(()=>window.scrollTo({top:document.getElementById('responses').getBoundingClientRect().top+scrollY-140,behavior:'instant'}));
  await page.screenshot({path:output+'/mobile-pinned.png'});
  await page.goto(origin+'/'); await page.goto(`${origin}/post/${post.id}`);
  await page.locator(`[data-comment-id="${root.id}"]`).waitFor();
  assert.equal(await page.locator('[data-comment-floor]').first().getAttribute('data-comment-floor'),root.id);
  checks.push('真实网络响应丢失后复用操作标识、只计一次；成功后本页置顶，离开再进入恢复热评排序；倒计时保留新草稿');
  await form.getByRole('button',{name:'发表评论',exact:true}).waitFor();
  await input.fill(text);
  let response=page.waitForResponse(response=>response.url().endsWith(`/posts/${post.id}/comments`) && response.request().method()==='POST');
  await form.getByRole('button',{name:'发表评论',exact:true}).click();
  let sentResponse=await response;
  if(sentResponse.status()===429) {
    assert.equal(await input.textContent(),text);
    assert.ok(Number(sentResponse.headers()['retry-after'])>0);
    response=page.waitForResponse(response=>response.url().endsWith(`/posts/${post.id}/comments`) && response.request().method()==='POST');
    await form.getByRole('button',{name:'发表评论',exact:true}).click();
    sentResponse=await response;
  }
  assert.equal(sentResponse.status(),200);
  const newId=(await sentResponse.json()).data.id;
  assert.notEqual(newId,publishedId);
  await page.locator(`[data-comment-id="${newId}"]`).waitFor();
  assert.equal(await page.locator('[data-comment-floor]').first().getAttribute('data-comment-floor'),newId);
  checks.push('冷却结束后允许再次发布相同文字，不做重复内容检测');
  await input.fill(`${marker} 再发一条`);
  response=page.waitForResponse(response=>response.url().endsWith(`/posts/${post.id}/comments`) && response.request().method()==='POST');
  await form.getByRole('button',{name:'发表评论',exact:true}).click();
  const newest=(await (await response).json()).data.id;
  await page.locator(`[data-comment-id="${newest}"]`).waitFor();
  assert.deepEqual((await page.locator('[data-comment-floor]').evaluateAll(nodes=>nodes.map(node=>node.dataset.commentFloor))).slice(0,2),[newest,newId]);
  const replyFloor=page.locator(`[data-comment-floor="${publishedId}"]`);
  await replyFloor.locator(`[data-comment-id="${publishedId}"]`).getByRole('button',{name:'回复',exact:true}).click();
  const replyForm=replyFloor.getByRole('form',{name:'楼内回复'});
  await replyForm.getByRole('textbox').fill(`${marker} 新的楼内回复`);
  response=page.waitForResponse(response=>response.url().endsWith(`/posts/${post.id}/comments`) && response.request().method()==='POST');
  const replyBatch=page.waitForResponse(response=>response.url().includes(`/comments/${publishedId}/replies?`));
  await replyForm.getByRole('button',{name:'发表回复',exact:true}).click();
  const child=(await (await response).json()).data;
  await replyBatch;
  await replyFloor.locator(`[data-comment-id="${child.id}"]`).waitFor();
  assert.equal(await replyFloor.locator(`[data-comment-id="${child.id}"]`).count(),1);
  checks.push('本次访问连续发布的主评论按入栈顺序显示；楼内新回复展开、置顶且服务端返回后不重复');
  assert.deepEqual(errors,[]);
  await writeFile(output+'/result.json',JSON.stringify({checks,errors},null,2));
  console.log(JSON.stringify({checks,errors},null,2));
} finally { await browser.close(); }
