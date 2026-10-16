import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {mkdir,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {createTestFetch} from './test-api-auth.mjs';

const marker=process.env.E2E_RUN_MARKER;
assert.match(marker||'',/^\[E2E_TEST\] follow-actions-\d+$/);
const stamp=marker.split('follow-actions-')[1],base='http://localhost:8080/api',origin='http://localhost:3000';
const request=createTestFetch(base),checks=[],errors=[],output='output/playwright/follow-actions-live';
const docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
await mkdir(output,{recursive:true});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function sql(value) {return execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:value,encoding:'utf8'}).trim();}
async function api(path,method='GET',body,token,key=randomUUID()) {
  const response=await request(base+path,{method,headers:{'Content-Type':'application/json','X-Bypass-Rate-Limit':'true','X-Idempotency-Key':key,...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});
  const result=await response.json();return {status:response.status,data:result.data,error:result.error,retry:Number(response.headers.get('Retry-After')),replayed:response.headers.get('Idempotency-Replayed')};
}
async function ok(path,method,body,token,key) {const result=await api(path,method,body,token,key);assert.equal(result.status,200,`${path}: ${JSON.stringify(result.error)}`);return result.data;}
async function until(load,predicate) {const end=Date.now()+25000;let result;do {result=await load();if(predicate(result))return result;await sleep(150);}while(Date.now()<end);assert.fail(`等待超时: ${JSON.stringify(result)}`);}
function pass(value) {checks.push(value);console.log('[PASS] '+value);}
async function account(label) {return ok('/auth/register','POST',{username:`E2E_TEST_f${label}_${stamp.slice(-9)}`,displayName:`${marker} ${label}`,password:'Password123!'});}
const owner=await account('owner'),a=await account('a'),b=await account('b'),c=await account('c'),d=await account('d');
for(const actor of [owner,a,b,c,d]) {
  assert.match(actor.user.id,/^\d+$/);
  assert.equal(sql(`SELECT LOCATE('${marker}',display_name)>0 FROM users WHERE id=${actor.user.id};`),'1');
}
const relation=`/users/${owner.user.username}/follow`,firstKey=randomUUID();
assert.equal((await ok(relation,'POST',null,a.token,firstKey)).isFollowing,true);
const limited=await api(relation,'DELETE',null,a.token);
assert.equal(limited.status,429);assert.equal(limited.error.code,'FOLLOW_RATE_LIMITED');assert.equal(limited.retry,1);
assert.equal(sql(`SELECT COUNT(*) FROM user_follows WHERE follower_id=${a.user.id} AND following_id=${owner.user.id};`),'1');
const duplicates=await Promise.all(Array.from({length:16},()=>api(relation,'POST',null,a.token)));
assert.ok(duplicates.every(result=>result.status===200 && result.data.isFollowing));
assert.equal(sql(`SELECT COUNT(*) FROM follow_actions WHERE user_id=${a.user.id};`),'1');
const replay=await api(relation,'POST',null,a.token,firstKey);assert.equal(replay.replayed,'true');
pass('真实 HTTP：一秒限频含 Retry-After，拒绝取消不改变关系；十六个不同操作标识重复关注只计一次');

await sleep(1100);await ok(relation,'DELETE',null,a.token);
await sleep(1100);await ok(relation,'POST',null,a.token);
await until(()=>Promise.resolve(sql(`SELECT COUNT(*) FROM notifications WHERE actor_id=${a.user.id} AND receiver_id=${owner.user.id} AND type='FOLLOW_USER';`)),value=>value==='1');
assert.equal(sql(`SELECT COUNT(*) FROM reliable_events WHERE event_type='NOTIFICATION' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.actorId'))='${a.user.id}' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.type'))='FOLLOW_USER';`),'1');
pass('真实 MQ 与 MySQL：取消后重新关注不重复产生十分钟内的通知意图及收件箱消息');
assert.equal((await api(`/users/missing_${stamp}/follow`,'POST',null,c.token)).status,404);
assert.equal((await api(`/users/${c.user.username}/follow`,'POST',null,c.token)).status,400);
assert.equal((await api(relation,'POST',null,c.token)).status,200);
assert.equal(sql(`SELECT COUNT(*) FROM follow_actions WHERE user_id=${c.user.id};`),'1');
pass('失败与自关注不消耗额度，正常首次关注仍可立即成功');

// 只对已核验本批标记的用户构造满窗口，避免通过改时钟或关闭防刷验证边界。
const seed=7100000000000000000n+BigInt(stamp)%100000000n*100n;
let fixture=`INSERT INTO follow_action_gates(user_id,last_changed_at) VALUES(${d.user.id},CURRENT_TIMESTAMP(6)-INTERVAL 2 SECOND);`;
for(let i=0;i<30;i++) fixture+=`INSERT INTO follow_actions(id,user_id,target_id,is_following,changed_at) VALUES(${seed+BigInt(i)},${d.user.id},${owner.user.id},true,CURRENT_TIMESTAMP(6)-INTERVAL ${i+5} SECOND);`;
fixture+=`UPDATE follow_action_gates SET recent_change_times=(SELECT GROUP_CONCAT(DATE_FORMAT(changed_at,'%Y-%m-%dT%H:%i:%s.%f') ORDER BY changed_at SEPARATOR ',') FROM follow_actions WHERE user_id=${d.user.id}) WHERE user_id=${d.user.id};`;
sql(fixture);
const burst=await api(relation,'POST',null,d.token);assert.equal(burst.status,429);assert.ok(burst.retry>=24 && burst.retry<=26);
assert.equal(sql(`SELECT COUNT(*) FROM user_follows WHERE follower_id=${d.user.id};`),'0');
await ok(relation,'POST',null,b.token);
pass('滚动一分钟三十次上限由真实 HTTP 拒绝并回滚，其他用户不受影响');

const community=await ok('/communities','POST',{slug:`e2e-follow-${stamp}`,name:marker,description:marker,category:'Living'},a.token);
const post=await ok('/posts','POST',{community:community.name,title:'[E2E_TEST] 关注',content:marker,isPublic:true},a.token);
const {chromium}=await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({channel:'chrome',headless:true});
try {
  const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  await context.addInitScript(token=>localStorage.setItem('luminous_token',token),owner.token);
  const page=await context.newPage();page.setDefaultTimeout(15000);page.on('pageerror',error=>errors.push(error.message));
  await page.goto(`${origin}/u/${owner.user.username}?tab=followers`);
  const row=page.locator(`[data-follow-user-id="${a.user.id}"]`);
  await row.getByRole('button',{name:`回关 ${a.user.displayName}`,exact:true}).click();
  await page.getByRole('link',{name:'查看关注列表，1 人',exact:true}).waitFor();
  await row.getByRole('button',{name:`取消关注 ${a.user.displayName}`,exact:true}).waitFor();
  await page.getByRole('link',{name:'关注列表',exact:true}).click();
  await page.locator(`[data-follow-user-id="${a.user.id}"]`).getByRole('button',{name:`取消关注 ${a.user.displayName}`,exact:true}).waitFor({state:'visible'});
  await until(()=>page.locator(`[data-follow-user-id="${a.user.id}"] button`).isEnabled(),Boolean);
  await page.locator(`[data-follow-user-id="${a.user.id}"] button`).click();
  await page.getByRole('heading',{name:'还没有关注任何人',exact:true}).waitFor();
  await page.getByRole('link',{name:'查看关注列表，0 人',exact:true}).waitFor();
  pass('真实浏览器：粉丝列表直接回关，按钮与主页人数同步，关注列表直接取消并移除该行');

  await page.getByRole('link',{name:'粉丝列表',exact:true}).click();
  await row.getByRole('link',{name:`查看 ${a.user.displayName} 的个人主页`,exact:true}).click();
  const header=page.locator('header.panel'),follow=header.getByRole('button',{name:'关注',exact:true});
  await follow.waitFor();await until(()=>follow.isEnabled(),Boolean);await follow.click();
  await header.getByRole('button',{name:'1 秒后',exact:true}).waitFor();
  assert.equal(await header.getByRole('button',{name:'1 秒后',exact:true}).isDisabled(),true);
  await header.getByRole('button',{name:'已关注',exact:true}).waitFor();
  await header.getByRole('link',{name:'查看粉丝列表，1 人',exact:true}).waitFor();
  assert.ok((await ok('/feed','GET',null,owner.token)).items.some(item=>item.id===post.id));
  await header.getByRole('button',{name:'已关注',exact:true}).click();
  await header.getByRole('button',{name:'关注',exact:true}).waitFor();
  assert.ok(!(await ok('/feed','GET',null,owner.token)).items.some(item=>item.id===post.id));
  pass('主页关注按钮共享禁用倒计时，粉丝数即时刷新；取消后旧关注动态缓存失效');

  // 注入一次响应丢失：请求已真实提交，重试必须复用同一操作标识。
  const followUrl=`**/api/users/${a.user.username}/follow`;
  let originalKey;
  await page.route(followUrl,async route=>{originalKey=route.request().headers()['x-idempotency-key'];const response=await route.fetch();assert.equal(response.status(),200);await route.abort('failed');},{times:1});
  await until(()=>follow.isEnabled(),Boolean);await follow.click();
  await page.getByText('网络连接中断，请重试。',{exact:true}).waitFor();
  let retryKey;
  page.on('request',req=>{if(req.url().endsWith(`/users/${a.user.username}/follow`)) retryKey=req.headers()['x-idempotency-key'];});
  // 统计刷新失败不能撤回关系，也不能把用户退出登录。
  await page.route('**/api/auth/me',route=>route.abort('failed'),{times:1});
  await follow.click();await header.getByRole('button',{name:'已关注',exact:true}).waitFor();
  assert.equal(retryKey,originalKey);
  assert.equal(sql(`SELECT COUNT(*) FROM follow_actions WHERE user_id=${owner.user.id};`),'5');
  assert.equal(await page.evaluate(()=>localStorage.getItem('luminous_token')),owner.token);
  pass('响应丢失重试复用操作标识，无额外额度；统计请求失败仍保留成功关注及登录');

  await header.getByRole('button',{name:'已关注',exact:true}).click();
  await follow.waitFor();await until(()=>follow.isEnabled(),Boolean);
  let abandonedKey,aborted=false;
  await page.route(followUrl,async route=>{abandonedKey=route.request().headers()['x-idempotency-key'];const response=await route.fetch();assert.equal(response.status(),200);await route.abort('failed');aborted=true;},{times:1});
  await follow.click();await until(()=>Promise.resolve(aborted),Boolean);
  await page.reload();
  await header.getByRole('button',{name:'已关注',exact:true}).waitFor();await sleep(1100);
  await header.getByRole('button',{name:'已关注',exact:true}).click();
  await follow.waitFor();await until(()=>follow.isEnabled(),Boolean);await follow.click();
  await header.getByRole('button',{name:'已关注',exact:true}).waitFor();
  assert.notEqual(retryKey,abandonedKey);
  assert.equal(sql(`SELECT COUNT(*) FROM follow_actions WHERE user_id=${owner.user.id};`),'9');
  assert.equal((await ok(`/users/${a.user.username}`,'GET',null,owner.token)).isFollowing,true);
  pass('响应丢失后刷新、成功取消、再次关注形成新操作，不重放过期的关注意图');

  await page.goto(`${origin}/u/${owner.user.id}?tab=followers`);
  await page.getByRole('button',{name:'编辑资料',exact:true}).waitFor();
  for(const width of [390,320]) {
    await page.setViewportSize({width,height:844});await row.waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:`${output}/mobile-${width}.png`,fullPage:true});
  }
  await page.evaluate(()=>document.documentElement.classList.add('dark'));
  await page.screenshot({path:`${output}/mobile-dark.png`,fullPage:true});
  await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:`${output}/desktop.png`,fullPage:true});
  assert.deepEqual(errors,[]);
  pass('数字 ID 个人主页正确识别本人，桌面及 390/320 手机与深色主题无横向溢出');
  const anonymous=await browser.newContext({viewport:{width:390,height:844}});
  const publicPage=await anonymous.newPage();
  await publicPage.goto(`${origin}/u/${owner.user.username}?tab=following`);
  await publicPage.locator(`[data-follow-user-id="${a.user.id}"]`).getByRole('button',{name:`关注 ${a.user.displayName}`,exact:true}).click();
  await publicPage.getByRole('dialog',{name:'登录 Luminous',exact:true}).waitFor();
  pass('匿名用户可浏览列表，点击关注提示登录');
  await anonymous.close();
  await writeFile(`${output}/report.json`,JSON.stringify({marker,checks,errors},null,2));
} finally {await browser.close();}
