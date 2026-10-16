import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {createTestFetch,requireDevTarget} from './test-api-auth.mjs';

const marker=process.env.E2E_RUN_MARKER;assert.match(marker||'',/^\[E2E_TEST\] lumi-chat-\d+$/);
const stamp=marker.split('lumi-chat-')[1],base='http://localhost:8080/api',origin='http://localhost:3000',output='output/playwright/lumi-chat';requireDevTarget(base);
const request=createTestFetch(base),checks=[],faults=[],answers=[],contrasts=[];
const docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
await mkdir(output,{recursive:true});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function sql(text){return execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:text,encoding:'utf8'}).trim();}
function pass(text){checks.push(text);console.log('[PASS] '+text);}
async function api(path,method='GET',body,token){const response=await request(base+path,{method,headers:{'Content-Type':'application/json','X-Idempotency-Key':randomUUID(),'X-Bypass-Rate-Limit':'true',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:response.status,...await response.json()};}
async function ok(path,method,body,token){const r=await api(path,method,body,token);assert.equal(r.status,200,path+': '+JSON.stringify(r));return r.data;}
async function account(label){return ok('/auth/register','POST',{username:`chat_${stamp}_${label}`,displayName:`${marker} ${label}`,password:'Password123!'});}
for(let i=0;i<50;i++){try{if((await fetch(base+'/users/profile-presets')).ok)break;}catch{}await sleep(1000);}
const user=await account('owner'),peer=await account('peer'),pager=await account('pager');
assert.equal((await api('/lumi/chat')).status,401);
const first=await ok('/lumi/chat','GET',null,user.token),other=await ok('/lumi/chat','GET',null,peer.token);
assert.notEqual(first.sessionId,other.sessionId);assert.equal(first.turns.length,0);assert.equal(first.enabled,true);
assert.equal((await api('/lumi/chat/messages','POST',{sessionId:first.sessionId,requestKey:randomUUID(),content:marker},peer.token)).status,409);
assert.equal((await api('/lumi/chat/messages','POST',{sessionId:first.sessionId,requestKey:randomUUID(),content:'字'.repeat(1001)},user.token)).status,400);
assert.equal((await api('/lumi/chat/messages','POST',{sessionId:first.sessionId,requestKey:randomUUID(),content:marker+' 联网查一下'},user.token)).status,409);
pass('匿名不可读，账号会话隔离，超长和联网请求不生成聊天任务');
const {chromium}=await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({channel:'chrome',headless:true});
async function context(actor){const c=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});if(actor)await c.addInitScript(token=>localStorage.setItem('luminous_token',token),actor.token);const p=await c.newPage();p.on('pageerror',e=>faults.push(e.message));return {c,p};}
async function capture(page,name){
  if(process.env.E2E_CHAT_SKIP_SCREENSHOTS==='true')return;
  await page.evaluate(async()=>{await document.fonts.ready;await Promise.all(Array.from(document.images).filter(img=>img.getBoundingClientRect().top<innerHeight).map(img=>img.decode().catch(()=>{})));await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  const contrast=await page.evaluate(()=>{
    const input=document.querySelector('#lumi-chat-input');if(!input)return null;
    const luminance=color=>color.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;}).reduce((sum,v,i)=>sum+v*[.2126,.7152,.0722][i],0);
    const foreground=luminance(getComputedStyle(input,'::placeholder').color),background=luminance(getComputedStyle(input).backgroundColor);
    return (Math.max(foreground,background)+.05)/(Math.min(foreground,background)+.05);
  });
  if(contrast!==null){assert.ok(contrast>=4.5,'聊天提示文字对比度不足');contrasts.push({capture:name,ratio:contrast});}
  await page.screenshot({path:`${output}/${name}.png`});
}
async function send(page,text){await page.getByLabel('发给 Lumi 的消息').fill(text);const promise=page.waitForResponse(r=>r.url().endsWith('/api/lumi/chat/messages')&&r.request().method()==='POST');await page.getByRole('button',{name:'发送',exact:true}).click();const response=await promise;assert.equal(response.status(),200);return {turn:(await response.json()).data,request:response.request().postDataJSON()};}
async function complete(actor,id){for(let i=0;i<30;i++){const page=await ok('/lumi/chat','GET',null,actor.token);const turn=page.turns.find(row=>row.id===id);if(turn&&['SUCCEEDED','FAILED'].includes(turn.status)){assert.equal(turn.status,'SUCCEEDED',JSON.stringify(turn));return turn;}await sleep(2000);}throw new Error('聊天完成超时');}
try{
  const anon=await context();await anon.p.goto(origin+'/lumi');await anon.p.getByText('登录后，和 Lumi 聊聊',{exact:true}).waitFor();await capture(anon.p,'anonymous-desktop');await anon.c.close();
  const {c,p}=await context(user);await p.goto(origin+'/');await p.getByRole('navigation',{name:'主导航',exact:true}).getByRole('link',{name:'Lumi AI',exact:true}).click();await p.getByLabel('发给 Lumi 的消息').waitFor();await p.getByRole('heading',{name:'有什么想和我聊聊的？'}).waitFor();await capture(p,'empty-desktop');await p.setViewportSize({width:390,height:844});await capture(p,'empty-mobile');await p.setViewportSize({width:1440,height:1000});
  const start=await send(p,marker+' 我的暗号是“晨光蓝”，请记住，仅回复“记住了”。');
  await p.getByText(/Lumi 正在排队|Lumi 正在思考/).waitFor();await capture(p,'pending-desktop');
  const repeated=await ok('/lumi/chat/messages','POST',start.request,user.token);assert.equal(repeated.id,start.turn.id);
  assert.equal((await api('/lumi/chat/messages','POST',{...start.request,content:marker+' 不同内容'},user.token)).status,409);
  assert.equal((await api('/lumi/chat/reset','POST',{sessionId:first.sessionId},user.token)).status,409);
  const a=await complete(user,start.turn.id);answers.push(a.answer);await p.locator(`[data-chat-turn="${a.id}"] [data-chat-answer]`).waitFor();
  await p.reload();await p.locator(`[data-chat-turn="${a.id}"] [data-chat-answer]`).waitFor();
  const next=await send(p,marker+' 我的暗号是什么？只写暗号。');const b=await complete(user,next.turn.id);assert.match(b.answer,/晨光蓝/);answers.push(b.answer);await p.locator(`[data-chat-turn="${b.id}"] [data-chat-answer]`).waitFor();await capture(p,'reply-desktop');
  pass('真实DeepSeek首轮与刷新后的追问成功；同一操作重发不重复生成，进行中不能清空');
  await p.setViewportSize({width:390,height:844});const mobile=await send(p,marker+' 再把我的暗号单独写一遍。');const d=await complete(user,mobile.turn.id);assert.match(d.answer,/晨光蓝/);answers.push(d.answer);await p.locator(`[data-chat-turn="${d.id}"] [data-chat-answer]`).waitFor();await capture(p,'reply-mobile');
  await p.evaluate(()=>{document.documentElement.classList.add('dark');localStorage.setItem('luminous_v2_theme','dark');});await capture(p,'dark-mobile');await p.setViewportSize({width:1440,height:1000});await capture(p,'dark-desktop');await p.evaluate(()=>{document.documentElement.classList.remove('dark');localStorage.setItem('luminous_v2_theme','light');});
  pass('手机入口、输入、持续追问、历史恢复、深浅色与两端横向布局通过');
  await p.getByRole('button',{name:'新对话',exact:true}).click();await capture(p,'reset-desktop');await p.setViewportSize({width:390,height:844});await capture(p,'reset-mobile');await p.getByRole('button',{name:'继续这段对话',exact:true}).click();assert.equal((await ok('/lumi/chat','GET',null,user.token)).turns.length,3);await p.getByRole('button',{name:'新对话',exact:true}).click();await p.getByRole('button',{name:'开始新对话',exact:true}).click();await p.getByRole('heading',{name:'有什么想和我聊聊的？'}).waitFor();
  assert.equal(sql(`SELECT COUNT(*) FROM lumi_chat_turns WHERE user_id=${user.user.id} AND (question<>'' OR answer IS NOT NULL)`),'0');assert.equal(sql(`SELECT COUNT(*) FROM lumi_chat_turns WHERE user_id=${user.user.id} AND status='SUCCEEDED'`),'3');
  assert.equal((await api('/lumi/chat/messages','POST',start.request,user.token)).status,409);
  assert.equal(sql(`SELECT COUNT(*) FROM idempotent_requests WHERE scope_hash=SHA2('user:${user.user.id}|POST|/api/lumi/chat/messages',256) OR scope_hash=SHA2('user:${user.user.id}|POST|/api/lumi/chat/reset',256)`),'0');
  pass('新对话取消不清空；确认后真正擦除旧正文，刷新不恢复旧上下文且成功额度保留');await c.close();
  const failedId=(8400000000000000000n+BigInt(stamp)*10n).toString();sql(`INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,status,message,quota_day,accepted_at,created_at,updated_at,attempts) VALUES(${failedId},${peer.user.id},${other.sessionId},'E2E_TEST_${stamp}','${marker} 请用一句话问好','FAILED','Lumi 暂时无法完成回答，请稍后重试',DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 SECOND),UTC_TIMESTAMP(),UTC_TIMESTAMP(),1)`);
  assert.equal((await api(`/lumi/chat/messages/${failedId}/retry`,'POST',{},user.token)).status,404);
  const f=await context(peer);await f.p.goto(origin+'/lumi');await f.p.getByRole('button',{name:'重试回答',exact:true}).waitFor();await capture(f.p,'failed-desktop');await f.p.setViewportSize({width:390,height:844});await capture(f.p,'failed-mobile');await f.p.getByRole('button',{name:'重试回答',exact:true}).click();const recovered=await complete(peer,failedId);assert.ok(recovered.answer);await f.p.locator(`[data-chat-turn="${failedId}"] [data-chat-answer]`).waitFor();assert.equal(await f.p.locator('[data-chat-turn]').count(),1);await f.c.close();
  pass('显式失败夹具两端可见，手机手动重试调用真实模型，同一条消息恢复且他人不能重试');
  const paged=await ok('/lumi/chat','GET',null,pager.token);const rows=Array.from({length:24},(_,i)=>`(${8500000000000000000n+BigInt(stamp)*100n+BigInt(i)},${pager.user.id},${paged.sessionId},'E2E_TEST_page_${i}','${marker} 较早问题${i}','${marker} 回答${i}','SUCCEEDED','Lumi 已回复',DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),UTC_TIMESTAMP(),UTC_TIMESTAMP(),UTC_TIMESTAMP())`);sql('INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,answer,status,message,quota_day,accepted_at,created_at,updated_at) VALUES'+rows.join(','));
  const history=await context(pager);await history.p.goto(origin+'/lumi');await history.p.getByRole('button',{name:'查看更早的消息'}).waitFor();assert.equal(await history.p.locator('[data-chat-turn]').count(),20);await history.p.getByRole('button',{name:'查看更早的消息'}).click();await history.p.waitForFunction(()=>document.querySelectorAll('[data-chat-turn]').length===24);await history.c.close();
  assert.equal((await ok('/lumi/chat','GET',null,peer.token)).turns.length,1);
  pass('当前对话首次20轮、向上按需加载剩余历史；不同账号不串记录');
}finally{await browser.close();}
assert.deepEqual(faults,[]);
const usage=JSON.parse(sql(`SELECT JSON_OBJECT('attempts',COUNT(*),'inputTokens',COALESCE(SUM(a.input_tokens),0),'outputTokens',COALESCE(SUM(a.output_tokens),0),'chargedMicros',COALESCE(SUM(a.charged_micros),0)) FROM lumi_attempts a JOIN lumi_chat_turns t ON t.id=a.task_id WHERE t.user_id IN (${user.user.id},${peer.user.id},${pager.user.id})`));assert.equal(usage.attempts,4);
await writeFile(`${output}/result.json`,JSON.stringify({marker,checks,answers,usage,contrasts,faults},null,2));
