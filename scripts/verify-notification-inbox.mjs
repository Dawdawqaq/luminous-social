import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {mkdir,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {createTestFetch} from './test-api-auth.mjs';

const marker=process.env.E2E_RUN_MARKER;
assert.match(marker||'',/^\[E2E_TEST\] notification-inbox-\d+$/);
const stamp=marker.split('notification-inbox-')[1],base='http://localhost:8080/api',origin='http://localhost:3000';
const request=createTestFetch(base),checks=[],errors=[],output='output/playwright/notification-inbox-live';
await mkdir(output,{recursive:true});
const docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
function sql(text) { return execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:text,encoding:'utf8'}).trim(); }
async function api(path,method='GET',body,token) {
  const response=await request(base+path,{method,headers:{'Content-Type':'application/json','X-Bypass-Rate-Limit':'true','X-Idempotency-Key':randomUUID(),...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,data:response.status===204?null:(await response.json()).data};
}
async function ok(path,method,body,token) { const result=await api(path,method,body,token); assert.equal(result.status,200,path); return result.data; }
async function until(load,predicate) { const deadline=Date.now()+25000; let result; do {result=await load(); if(predicate(result)) return result; await new Promise(resolve=>setTimeout(resolve,250));} while(Date.now()<deadline); assert.fail(`通知消费等待超时: ${JSON.stringify(result)}`); }
async function account(label) { return ok('/auth/register','POST',{username:`E2E_TEST_n${label}_${stamp.slice(-9)}`,displayName:`${marker} ${label}`,password:'Password123!'}); }
const owner=await account('owner'),a=await account('a'),b=await account('b'),c=await account('c'),d=await account('d');
const receiver=String(owner.user.id); assert.match(receiver,/^\d+$/);
const community=await ok('/communities','POST',{slug:`e2e-inbox-${stamp}`,name:marker,description:marker,category:'Living'},owner.token);
const post=await ok('/posts','POST',{community:community.name,title:'[E2E_TEST] 通知',content:marker,isPublic:true},owner.token);
const root=await ok(`/posts/${post.id}/comments`,'POST',{content:marker},owner.token);
const inbox=(type='ALL',cursor)=>ok(`/notifications/inbox?type=${type}&size=20${cursor?`&cursor=${encodeURIComponent(cursor)}`:''}`,'GET',null,owner.token);
const likes=()=>inbox('LIKE');
await ok(`/posts/${post.id}/vote`,'POST',{value:1},a.token);
let first=await until(likes,result=>result.items.some(n=>n.type==='LIKE_POST'));
let postNotice=first.items.find(n=>n.type==='LIKE_POST'); assert.equal(postNotice.interactionCount,1);
assert.ok(Math.abs(Date.now()-Date.parse(postNotice.updatedAt))<10000,'通知更新时间必须与真实时间一致');
await ok(`/notifications/${postNotice.id}/read?version=1`,'PUT',null,owner.token);
await Promise.all([b,c].map(actor=>ok(`/posts/${post.id}/vote`,'POST',{value:1},actor.token)));
postNotice=(await until(likes,result=>result.items.find(n=>n.type==='LIKE_POST')?.interactionCount===3)).items.find(n=>n.type==='LIKE_POST');
assert.equal(postNotice.isRead,false);
await ok(`/notifications/${postNotice.id}/read?version=1`,'PUT',null,owner.token);
assert.equal((await likes()).items.find(n=>n.id===postNotice.id).isRead,false);
await ok(`/notifications/${postNotice.id}/read?version=3`,'PUT',null,owner.token);
assert.equal((await likes()).items.find(n=>n.id===postNotice.id).isRead,true);
checks.push('真实帖子赞同聚合为三人；已读后新增互动恢复未读；旧展示版本不会读掉新互动');
const replayId=sql(`SELECT event_id FROM reliable_events WHERE event_type='NOTIFICATION' AND user_id=${receiver} AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.type'))='LIKE_POST' LIMIT 1;`);
assert.match(replayId,/^[0-9a-f-]{36}$/);
sql(`UPDATE reliable_events SET status='PENDING',next_attempt_at=UTC_TIMESTAMP WHERE event_id='${replayId}';`);
await until(()=>Promise.resolve(sql(`SELECT status FROM reliable_events WHERE event_id='${replayId}';`)),status=>status==='CONSUMED');
assert.equal((await likes()).items.find(n=>n.type==='LIKE_POST').interactionCount,3);
checks.push('真实可靠事件重放复用原事件编号，重复消费不增加聚合人数');


await ok(`/posts/${post.id}/vote`,'POST',{value:1},a.token);
await ok(`/posts/${post.id}/vote`,'POST',{value:0},a.token);
await ok(`/posts/${post.id}/vote`,'POST',{value:-1},a.token);
await ok(`/posts/${post.id}/vote`,'POST',{value:1},a.token);
await ok(`/posts/${post.id}/vote`,'POST',{value:-1},d.token);
await ok(`/posts/${post.id}/vote`,'POST',{value:1},owner.token);
await until(()=>Promise.resolve(sql(`SELECT COUNT(*) FROM reliable_events WHERE event_type='NOTIFICATION' AND user_id=${receiver} AND status IN ('PENDING','SENT');`)),n=>n==='0');
assert.equal((await likes()).items.find(n=>n.type==='LIKE_POST').interactionCount,3);
checks.push('重复赞同、取消后重新赞同不重复计数；点踩与自己的赞同不产生通知');

await Promise.all([a,b].map(actor=>ok(`/comments/${root.id}/vote`,'POST',{value:1},actor.token)));
let commentNotice=(await until(likes,result=>result.items.find(n=>n.type==='LIKE_COMMENT')?.interactionCount===2)).items.find(n=>n.type==='LIKE_COMMENT');
assert.ok(commentNotice.target.targetUrl.includes(`comment=${root.id}`));
const reply=await ok(`/posts/${post.id}/comments`,'POST',{content:`${marker} 楼内 [猫猫开心]`,replyToCommentId:root.id},c.token);
await until(()=>inbox('COMMENT'),result=>result.items.some(n=>n.type==='REPLY_COMMENT'));
assert.equal((await ok(`/posts/${post.id}/comments/${reply.id}/context`,'GET',null,owner.token)).floor.id,root.id);
await ok(`/users/${owner.user.username}/follow`,'POST',null,a.token);
await until(()=>inbox('FOLLOW'),result=>result.items.length>0);
checks.push('评论及楼内回复赞同进入赞同分类；回复通知包含具体评论位置；关注分类独立');

assert.equal((await api(`/notifications/${postNotice.id}`,'DELETE',null,a.token)).status,403);
await ok(`/notifications/${postNotice.id}`,'DELETE',null,owner.token);
assert.equal(sql(`SELECT deleted_at IS NOT NULL FROM notifications WHERE id=${postNotice.id};`),'1');
assert.ok(!(await likes()).items.some(n=>n.id===postNotice.id));
await ok(`/posts/${post.id}/vote`,'POST',{value:1},d.token);
const newLike=(await until(likes,result=>result.items.some(n=>n.type==='LIKE_POST'))).items.find(n=>n.type==='LIKE_POST');
assert.notEqual(newLike.id,postNotice.id); assert.equal(newLike.interactionCount,1);
await ok('/notifications/read-all','PUT',null,owner.token);
assert.equal((await ok('/notifications/summary','GET',null,owner.token)).unreadCount,0);
assert.ok((await inbox()).items.length>0);
await ok(`/comments/${root.id}/vote`,'POST',{value:1},d.token);
await until(()=>ok('/notifications/summary','GET',null,owner.token),result=>result.unreadCount===1);
checks.push('通知软删除与权限隔离；新增互动创建新聚合行而不复活删除项；全部已读保留历史，新互动仍未读');

// 只为本批接收者构造大量带标记通知，验证分页与未加载的未读项。
const seed=7000000000000000000n+BigInt(stamp)%100000000n*100n;
let insert='';
for(let i=0;i<45;i++) insert+=`INSERT INTO notifications(id,receiver_id,actor_id,type,target_type,target_id,content_preview,is_read,created_at,updated_at,read_version) VALUES(${seed+BigInt(i)},${receiver},${a.user.id},'COMMENT_POST','POST',${post.id},'${marker} 收件箱 ${i}',${i<15?'false':'true'},UTC_TIMESTAMP-INTERVAL 10 MINUTE,UTC_TIMESTAMP-INTERVAL ${100-i} SECOND,${i<15?0:1});\n`;
sql(insert);
const start=await inbox('COMMENT'); assert.equal(start.items.length,20); assert.equal(start.hasNext,true); assert.equal(start.items.every(n=>n.isRead),true);
const nextId=seed+90n;
sql(`INSERT INTO notifications(id,receiver_id,actor_id,type,target_type,target_id,content_preview,is_read) VALUES(${nextId},${receiver},${a.user.id},'COMMENT_POST','POST',${post.id},'${marker} 翻页期间的新通知',false);`);
const collected=[...start.items]; let cursor=start.nextCursor;
while(cursor) { const page=await inbox('COMMENT',cursor);collected.push(...page.items);cursor=page.nextCursor; }
assert.equal(new Set(collected.map(n=>n.id)).size,collected.length); assert.ok(!collected.some(n=>n.id===String(nextId))); assert.ok(collected.length>=45);
assert.equal((await api('/notifications/inbox?type=LIKE&cursor='+encodeURIComponent(start.nextCursor),'GET',null,owner.token)).status,400);
assert.equal((await api('/notifications/inbox?size=99','GET',null,owner.token)).status,400);
checks.push('二十条按需分页，无重复；新增消息不打乱已读取位置；游标绑定用户分类；第一批全已读但历史有未读');

const {chromium}=await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({channel:'chrome',headless:true});
try {
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  await context.addInitScript(token=>localStorage.setItem('luminous_token',token),owner.token);
  const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));
  await page.goto(origin);await page.getByRole('button',{name:/^通知(?:，|$)/}).click();
  const dialog=page.getByRole('dialog',{name:'通知',exact:true});
  await dialog.locator('[data-notification-id]').first().waitFor();
  await dialog.getByRole('tab',{name:/评论/}).click();
  await dialog.locator('[data-notification-id]').first().waitFor();
  assert.equal(await dialog.getByRole('button',{name:'全部已读',exact:true}).isDisabled(),false);
  await dialog.locator('#notification-list').evaluate(el=>el.scrollTop=el.scrollHeight);
  await until(()=>dialog.locator('[data-notification-id]').count(),count=>count>20);
  await dialog.getByRole('tab',{name:/赞同/}).click();
  await dialog.locator(`[data-notification-id="${commentNotice.id}"]`).waitFor();
  await page.screenshot({path:output+'/desktop.png'});
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:output+'/mobile.png'});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  const action=dialog.locator(`[data-notification-id="${commentNotice.id}"]`).getByRole('button').first();
  await action.click();await page.locator(`[data-comment-id="${root.id}"]`).waitFor();assert.ok(page.url().includes(`comment=${root.id}`));
  await page.getByRole('button',{name:/^通知(?:，|$)/}).click();await dialog.getByRole('tab',{name:/评论/}).click();
  const replies=await inbox('COMMENT');const replyNotice=replies.items.find(n=>n.type==='REPLY_COMMENT');
  await dialog.getByRole('tab',{name:/全部/}).click();
  await dialog.locator('#notification-list').evaluate(el=>el.scrollTop=el.scrollHeight);
  await until(()=>dialog.locator(`[data-notification-id="${replyNotice.id}"]`).count(),count=>count>0);
  await dialog.locator(`[data-notification-id="${replyNotice.id}"]`).getByRole('button').first().click();
  await page.locator(`[data-comment-id="${reply.id}"]`).waitFor();
  checks.push('桌面和手机无横向溢出；滚动触发追加；未加载项不阻止全部已读；点击通知定位根评论与楼内回复');
  await page.getByRole('button',{name:/^通知(?:，|$)/}).click();
  let failFollow=true;
  await page.route('**/api/notifications/inbox?type=FOLLOW**',route=> {if(failFollow){failFollow=false;return route.abort();}return route.continue();});
  await dialog.getByRole('tab',{name:/关注/}).click();
  await dialog.getByText('通知加载失败',{exact:true}).waitFor();
  await dialog.getByRole('button',{name:'重新加载',exact:true}).click();
  await dialog.locator('[data-notification-id]').first().waitFor();
  await page.setViewportSize({width:320,height:720});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await dialog.getByRole('tab',{name:/关注/}).press('ArrowRight');
  assert.equal(await dialog.getByRole('tab',{name:/系统/}).getAttribute('aria-selected'),'true');
  await page.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});
  checks.push('加载失败可重试；三百二十像素无横向溢出；分类支持方向键与 Escape');

  await context.close();
} finally {await browser.close();}
assert.deepEqual(errors,[]);
await writeFile(output+'/validation.json',JSON.stringify({checkedAt:new Date().toISOString(),checks,errors},null,2));
console.log(JSON.stringify({passed:checks.length,checks,errors}));
