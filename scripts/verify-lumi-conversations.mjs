import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {createTestFetch,requireDevTarget} from './test-api-auth.mjs';

const marker=process.env.E2E_RUN_MARKER;assert.match(marker||'',/^\[E2E_TEST\] lumi-chat-\d+$/);
const stamp=marker.split('lumi-chat-')[1],base='http://localhost:8080/api',origin='http://localhost:3000',output='output/playwright/lumi-conversations';requireDevTarget(base);
const request=createTestFetch(base),docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
const captureOnly=process.env.E2E_CHAT_CAPTURE_ONLY==='true',checks=[],faults=[],captures=[],answers=[];let failure=null,realPassed=false;
await mkdir(output,{recursive:true});const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function sql(query){return execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:query,encoding:'utf8'}).trim();}
const hex=text=>`CONVERT(0x${Buffer.from(text).toString('hex')} USING utf8mb4)`;
function pass(text){checks.push(text);console.log('[PASS] '+text);}
async function api(path,method='GET',body,token){const response=await request(base+path,{method,headers:{'Content-Type':'application/json','X-Idempotency-Key':randomUUID(),'X-Bypass-Rate-Limit':'true',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:response.status,...await response.json()};}
async function ok(path,method,body,token){const r=await api(path,method,body,token);assert.equal(r.status,200,path+': '+JSON.stringify(r));return r.data;}
async function account(label){return ok('/auth/register','POST',{username:`chmulti_${stamp}_${label}`,displayName:`${marker} ${label}`,password:'Password123!'});}
async function until(check,message){for(let i=0;i<80;i++){if(await check())return;await sleep(100);}throw new Error(message);}
for(let i=0;i<25;i++){try{if((await fetch(base+'/users/profile-presets')).ok)break;}catch{}await sleep(1000);}
const owner=await account('owner'),peer=await account('peer'),fixture=await account('fixture');
const first=await ok('/lumi/chat','GET',null,owner.token),sample=await ok('/lumi/chat','GET',null,fixture.token);
const {chromium}=await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);const browser=await chromium.launch({channel:'chrome',headless:true});
async function context(actor){const c=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce',permissions:['clipboard-read','clipboard-write']});await c.addInitScript(({token,key})=>{if(!sessionStorage.getItem(key)){localStorage.setItem('luminous_token',token);sessionStorage.setItem(key,'1');}},{token:actor.token,key:'E2E_TEST_init_'+stamp});const p=await c.newPage();p.setDefaultTimeout(15000);p.setDefaultNavigationTimeout(20000);p.on('pageerror',e=>faults.push(e.message));return {c,p};}
async function read(actor,id){return ok(`/lumi/chat?sessionId=${id}`,'GET',null,actor.token);}
async function terminal(actor,id,session){for(let i=0;i<80;i++){const page=await read(actor,session),turn=page.turns.find(row=>row.id===id);if(turn&&!['PENDING','RUNNING','STOPPING'].includes(turn.status))return turn;await sleep(500);}throw new Error('模型任务结束超时');}
async function create(actor){return ok('/lumi/chat/conversations','POST',{requestKey:randomUUID()},actor.token);}
async function send(p,text){await p.getByLabel('发给 Lumi 的消息').fill(text);const response=p.waitForResponse(r=>r.url().endsWith('/api/lumi/chat/messages')&&r.request().method()==='POST');await p.getByRole('button',{name:'发送',exact:true}).click();const r=await response;assert.equal(r.status(),200);return (await r.json()).data;}
async function select(p,id){
  const mobile=await p.evaluate(()=>matchMedia('(max-width:1100px)').matches);let scope=p.locator('.lumi-conversations-desktop');
  if(mobile){await p.getByRole('button',{name:'打开对话列表'}).click();scope=p.getByRole('dialog',{name:'对话',exact:true});}
  await scope.locator(`[data-conversation-id="${id}"] .lumi-conversation-open`).click();await p.waitForURL(url=>url.searchParams.get('chat')===id);await until(async()=>await p.getByLabel('发给 Lumi 的消息').isEnabled(),'会话输入未恢复');
}
async function capture(p,name){
  await p.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.images].filter(img=>{const box=img.getBoundingClientRect();return box.width>0&&box.height>0&&box.top<innerHeight&&box.bottom>0;}).map(img=>Promise.race([img.decode(),new Promise((_,reject)=>setTimeout(()=>reject(new Error('可见图片加载超时')),5000))])));await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));window.scrollTo(0,0);});
  const metrics=await p.evaluate(()=>({viewport:[innerWidth,innerHeight],documentHeight:document.documentElement.scrollHeight,scrollY,overflow:document.documentElement.scrollWidth>innerWidth,buttonBottom:document.querySelector('.lumi-chat-composer button')?.getBoundingClientRect().bottom,inputFont:getComputedStyle(document.querySelector('#lumi-chat-input')).fontSize,largeNodes:[...document.querySelectorAll('body *')].map(el=>{const box=el.getBoundingClientRect(),css=getComputedStyle(el);return {tag:el.tagName,classes:el.className,top:box.top,bottom:box.bottom,height:box.height,overflow:css.overflow,position:css.position};}).filter(row=>row.bottom>innerHeight+200&&row.height>100).sort((a,b)=>b.bottom-a.bottom).slice(0,8)}));
  if(metrics.documentHeight>metrics.viewport[1]+200){await writeFile(`${output}/layout-diagnostic.json`,JSON.stringify(metrics,null,2));throw new Error('聊天外部出现异常页面高度');}
  assert.equal(metrics.overflow,false);assert.equal(metrics.inputFont,'16px');if(!await p.getByRole('dialog').count())assert.ok(metrics.buttonBottom<=metrics.viewport[1]);
  await p.screenshot({path:`${output}/${name}.png`,fullPage:true});captures.push({name,...metrics});
}
try{
  assert.equal((await api('/lumi/chat/conversations')).status,401);
  if(!captureOnly){
    const live=await context(owner);await live.p.goto(origin+`/lumi?chat=${first.sessionId}`);await live.p.getByRole('heading',{name:'有什么想和我聊聊的？'}).waitFor();
    const a=await send(live.p,marker+' 我的暗号是晨光蓝，请记住，再用Markdown写三条学习建议，每条约40字。');
    await live.p.getByLabel('发给 Lumi 的消息').fill(marker+' A的未发送草稿');
    const createdResponse=live.p.waitForResponse(r=>r.url().endsWith('/api/lumi/chat/conversations')&&r.request().method()==='POST');await live.p.getByRole('button',{name:'新对话',exact:true}).click();const b=(await (await createdResponse).json()).data;
    await live.p.waitForURL(url=>url.searchParams.get('chat')===b.sessionId);await until(async()=>await live.p.getByLabel('发给 Lumi 的消息').isEnabled(),'新对话未就绪');assert.equal(await live.p.getByLabel('发给 Lumi 的消息').inputValue(),'');
    await live.p.getByLabel('发给 Lumi 的消息').fill(marker+' B的未发送草稿');await select(live.p,first.sessionId);assert.equal(await live.p.getByLabel('发给 Lumi 的消息').inputValue(),marker+' A的未发送草稿');await select(live.p,b.sessionId);assert.equal(await live.p.getByLabel('发给 Lumi 的消息').inputValue(),marker+' B的未发送草稿');
    const firstAnswer=await terminal(owner,a.id,first.sessionId);assert.equal(firstAnswer.status,'SUCCEEDED');assert.equal((await read(owner,b.sessionId)).turns.length,0);assert.equal(sql(`SELECT COUNT(*) FROM lumi_attempts WHERE task_id=${a.id}`),'1');
    const second=await send(live.p,marker+' 这是独立的新话题，请只回复“晚星黄”。');const secondAnswer=await terminal(owner,second.id,b.sessionId);assert.equal(secondAnswer.status,'SUCCEEDED');assert.match(secondAnswer.answer,/晚星黄/);assert.doesNotMatch(secondAnswer.answer,/晨光蓝/);
    await select(live.p,first.sessionId);const third=await send(live.p,marker+' 我的暗号是什么？只写暗号。');const thirdAnswer=await terminal(owner,third.id,first.sessionId);assert.match(thirdAnswer.answer,/晨光蓝/);answers.push(firstAnswer.answer,secondAnswer.answer,thirdAnswer.answer);
    pass('真实模型在A/B独立问答，生成中另建会话不清空A或重复调用；A/B草稿分别恢复');
    for(const [path,method,body] of [[`/lumi/chat?sessionId=${first.sessionId}`,'GET',null],[`/lumi/chat/conversations/${first.sessionId}`,'PUT',{title:'[E2E_TEST] 越权'}],[`/lumi/chat/conversations/${first.sessionId}`,'DELETE',null],[`/lumi/chat/messages/${a.id}/stop`,'POST',{}]])assert.equal((await api(path,method,body,peer.token)).status,404);
    const foreign=await fetch(origin+`/api/lumi/chat/messages/${a.id}/stream`,{headers:{Authorization:`Bearer ${peer.token}`,Accept:'text/event-stream, application/json'}});assert.equal(foreign.status,404);
    const c=await create(owner),partialId=(BigInt(c.sessionId)-1n).toString();
    sql(`INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,answer,status,message,quota_day,accepted_at,created_at,updated_at,attempts,revision) VALUES(${partialId},${owner.user.id},${c.sessionId},'E2E_TEST_partial_${stamp}',${hex(marker+' 写一个故事，主角叫云团，在月球寻找蓝黄四角星。')},${hex('[E2E_TEST] 云团踏上月球，开始寻找蓝黄四角星。')},'STOPPED','已停止',DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 SECOND),UTC_TIMESTAMP(),UTC_TIMESTAMP(),1,3)`);
    await live.p.goto(origin+`/lumi?chat=${c.sessionId}`);await live.p.getByText('已停止',{exact:true}).waitFor();const continued=await send(live.p,marker+' 继续上一个故事，只用一句话，明确写出主角原名和他在寻找的物品。');const continuedAnswer=await terminal(owner,continued.id,c.sessionId);assert.equal(continuedAnswer.status,'SUCCEEDED');assert.match(continuedAnswer.answer,/云团/);assert.match(continuedAnswer.answer,/星/);answers.push(continuedAnswer.answer);
    pass('账号不能越权读取、重命名、删除或订阅；真实模型能参考明确标记的停止片段继续');
    const beforeFees=sql(`SELECT SUM(charged_micros) FROM lumi_attempts a JOIN lumi_chat_turns t ON t.id=a.task_id WHERE t.user_id=${owner.user.id}`);await live.p.getByLabel('发给 Lumi 的消息').fill(marker+' 删除C的草稿');
    await live.p.locator(`.lumi-conversations-desktop [data-conversation-id="${c.sessionId}"]`).getByRole('button',{name:/删除对话/}).click();await live.p.getByRole('button',{name:'确认删除',exact:true}).click();await until(async()=>new URL(live.p.url()).searchParams.get('chat')!==c.sessionId,'删除后未切到可用会话');
    assert.equal((await api(`/lumi/chat?sessionId=${c.sessionId}`,'GET',null,owner.token)).status,404);assert.equal(sql(`SELECT COUNT(*) FROM lumi_chat_turns WHERE user_id=${owner.user.id} AND session_id=${c.sessionId} AND (question<>'' OR answer IS NOT NULL)`),'0');assert.equal(sql(`SELECT SUM(charged_micros) FROM lumi_attempts a JOIN lumi_chat_turns t ON t.id=a.task_id WHERE t.user_id=${owner.user.id}`),beforeFees);assert.ok((await read(owner,first.sessionId)).turns.length>0);
    pass('猫猫删除确认清除指定对话与草稿，其他会话保留，费用事实不退还');realPassed=true;await live.c.close();
  }
  const a=sample,b=await create(fixture);await ok(`/lumi/chat/conversations/${a.sessionId}`,'PUT',{title:'星际灵感'},fixture.token);await ok(`/lumi/chat/conversations/${b.sessionId}`,'PUT',{title:'日常笔记'},fixture.token);
  const messageBase=9000000000000000000n+BigInt(stamp)*100n;
  const markdown='## 把灵感留住\n\n这是 [E2E_TEST] 排版样例。**先记录，再整理**，从一个小问题开始。\n\n- 留下一句核心想法。\n- 回头把相关内容连起来。\n\n```java\nString note = "星际灵感";\nSystem.out.println(note);\n```\n\n| 记录 | 回顾 |\n| --- | --- |\n| 随手写下 | 每周整理 |';
  const rows=Array.from({length:40},(_,index)=>`(${messageBase+BigInt(index)},${fixture.user.id},${a.sessionId},'E2E_TEST_history_${stamp}_${index}',${hex(marker+' 展示问题 '+index)},${hex(index===39?markdown:'[E2E_TEST] 已保存的回答 '+index)},'SUCCEEDED','Lumi 已回复',DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),DATE_SUB(UTC_TIMESTAMP(),INTERVAL 10 SECOND),UTC_TIMESTAMP(),UTC_TIMESTAMP(),2)`);
  sql('INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,answer,status,message,quota_day,accepted_at,created_at,updated_at,revision) VALUES'+rows.join(','));
  const conversations=Array.from({length:24},(_,index)=>`(${9100000000000000000n+BigInt(stamp)*100n+BigInt(index)},${fixture.user.id},${hex(index%2?'[E2E_TEST] 生活随记 '+index:'[E2E_TEST] 阅读清单 '+index)},UTC_TIMESTAMP(),DATE_SUB(UTC_TIMESTAMP(),INTERVAL ${index+2} MINUTE))`);sql('INSERT INTO lumi_chat_conversations(id,user_id,title,created_at,activity_at) VALUES'+conversations.join(','));
  const visual=await context(fixture);await visual.p.goto(origin+`/lumi?chat=${a.sessionId}`);const input=visual.p.getByLabel('发给 Lumi 的消息');await input.waitFor();await until(async()=>await visual.p.locator('[data-chat-turn]').count()===20,'消息首屏不是20条');await until(async()=>await visual.p.locator('.lumi-conversations-desktop [data-conversation-id]').count()===20,'会话首屏不是20条');
  await visual.p.locator('.lumi-conversations-desktop').getByRole('button',{name:'加载更多对话'}).click();await until(async()=>await visual.p.locator('.lumi-conversations-desktop [data-conversation-id]').count()===26,'会话续载失败');
  await visual.p.getByRole('button',{name:'查看更早的消息'}).click();await until(async()=>await visual.p.locator('[data-chat-turn]').count()===40,'消息续载失败');
  await input.fill(marker+' A草稿\n第二行');await visual.p.locator('.lumi-chat-messages').evaluate(el=>el.scrollTop=0);await sleep(120);await select(visual.p,b.sessionId);await input.fill(marker+' B草稿');await select(visual.p,a.sessionId);assert.equal(await input.inputValue(),marker+' A草稿\n第二行');assert.equal(await visual.p.locator('[data-chat-turn]').count(),40);assert.ok(await visual.p.locator('.lumi-chat-messages').evaluate(el=>el.scrollTop<10));
  let delayed=false;await visual.p.route('**/api/lumi/chat?*',async route=>{const url=new URL(route.request().url());if(url.searchParams.get('sessionId')===a.sessionId&&!delayed){delayed=true;const response=await route.fetch();await sleep(1200);await route.fulfill({response});}else await route.continue();});
  await select(visual.p,b.sessionId);await select(visual.p,a.sessionId);await select(visual.p,b.sessionId);await sleep(1500);assert.equal(await visual.p.locator('[data-chat-turn]').count(),0);assert.equal(await input.inputValue(),marker+' B草稿');await visual.p.unroute('**/api/lumi/chat?*');
  pass('会话和消息分别20条续载；切换恢复已加载历史、草稿和阅读位置，迟到A响应不写入B');
  await select(visual.p,a.sessionId);await input.fill('');await visual.p.locator('.lumi-chat-messages').evaluate(el=>el.scrollTop=el.scrollHeight);await capture(visual.p,'desktop');await visual.p.setViewportSize({width:768,height:1024});await capture(visual.p,'tablet');await visual.p.setViewportSize({width:390,height:844});await capture(visual.p,'mobile');
  await visual.p.getByRole('button',{name:'打开对话列表'}).click();await capture(visual.p,'mobile-list');await visual.p.getByRole('dialog',{name:'对话',exact:true}).locator(`[data-conversation-id="${a.sessionId}"]`).getByRole('button',{name:/重命名对话/}).click();await capture(visual.p,'mobile-rename');await visual.p.getByLabel('对话名称').fill('星际灵感与小计划');await visual.p.getByRole('button',{name:'保存名称'}).click();await until(async()=>await visual.p.locator('.lumi-chat-current-title').textContent()==='星际灵感与小计划','重命名未同步');
  await visual.p.getByRole('button',{name:'打开对话列表'}).click();await visual.p.getByRole('dialog',{name:'对话',exact:true}).locator(`[data-conversation-id="${b.sessionId}"]`).getByRole('button',{name:/删除对话/}).click();await visual.p.locator('.delete-confirm-cat').waitFor();await capture(visual.p,'mobile-delete');await visual.p.getByRole('button',{name:'取消',exact:true}).click();
  const activeId=(messageBase+39n).toString();sql(`UPDATE lumi_chat_turns SET status='RUNNING',lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR),lease_token='E2E_TEST_background',message='Lumi 正在回答',revision=revision+1 WHERE id=${activeId}`);
  await visual.p.reload();await visual.p.getByRole('button',{name:'停止生成',exact:true}).waitFor();await select(visual.p,b.sessionId);await visual.p.getByText('Lumi 正在处理你的另一条请求，可以先写草稿。',{exact:true}).waitFor();assert.equal(await visual.p.getByRole('button',{name:'等待其他回答',exact:true}).isDisabled(),true);assert.equal((await api(`/lumi/chat/conversations/${a.sessionId}`,'DELETE',null,fixture.token)).status,409);await capture(visual.p,'mobile-background');await visual.p.setViewportSize({width:1440,height:1000});await capture(visual.p,'desktop-background');
  sql(`UPDATE lumi_chat_turns SET status='STOPPED',lease_until=NULL,message='已停止',revision=revision+1 WHERE id=${activeId}`);await until(async()=>!await visual.p.getByRole('button',{name:'等待其他回答',exact:true}).count(),'其他会话完成后未解除等待');
  await select(visual.p,a.sessionId);await visual.p.setViewportSize({width:390,height:844});await visual.p.evaluate(()=>document.documentElement.classList.add('dark'));await capture(visual.p,'mobile-dark');await visual.p.evaluate(()=>document.documentElement.classList.remove('dark'));
  await visual.p.evaluate(()=>{Object.defineProperties(window.visualViewport,{height:{configurable:true,get:()=>480},offsetTop:{configurable:true,get:()=>0}});window.dispatchEvent(new Event('resize'));});await visual.p.waitForFunction(()=>document.documentElement.classList.contains('lumi-keyboard'));assert.ok(await visual.p.locator('.lumi-chat-composer button').evaluate(el=>el.getBoundingClientRect().bottom<=480));await capture(visual.p,'mobile-keyboard-simulated');
  pass('两端列表、手机抽屉、重命名、猫猫删除确认、后台生成提示、深色和模拟键盘布局通过');
  await visual.p.evaluate(token=>localStorage.setItem('luminous_token',token),peer.token);await visual.p.reload();await visual.p.getByRole('alert').filter({hasText:'这段对话暂不可用'}).waitFor();assert.equal(await visual.p.locator('[data-chat-turn]').count(),0);assert.equal(await input.inputValue(),'');
  pass('切换账号后的旧会话地址不可读，旧消息和草稿不显示');await visual.c.close();assert.deepEqual(faults,[]);
}catch(error){failure={message:error.message,stack:error.stack};throw error;}
finally{
  await browser.close();
  const usage=JSON.parse(sql(`SELECT JSON_OBJECT('attempts',COUNT(*),'inputTokens',COALESCE(SUM(a.input_tokens),0),'outputTokens',COALESCE(SUM(a.output_tokens),0),'chargedMicros',COALESCE(SUM(a.charged_micros),0)) FROM lumi_attempts a JOIN lumi_chat_turns t ON t.id=a.task_id WHERE t.user_id IN (${owner.user.id},${peer.user.id},${fixture.user.id})`));
  const result={marker,captureOnly,realPassed,checks,answers,usage,captures,faults,failure,keyboardEvidence:'Chrome emulated viewports and synthesized visualViewport=480; no physical keyboard or Safari proof'};
  await writeFile(`${output}/result.json`,JSON.stringify(result,null,2));await writeFile(`${output}/result-${stamp}.json`,JSON.stringify(result,null,2));
}
