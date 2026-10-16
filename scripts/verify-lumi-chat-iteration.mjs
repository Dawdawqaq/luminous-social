import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {createTestFetch,requireDevTarget} from './test-api-auth.mjs';

const marker=process.env.E2E_RUN_MARKER;assert.match(marker||'',/^\[E2E_TEST\] lumi-chat-\d+$/);
const stamp=marker.split('lumi-chat-')[1],base='http://localhost:8080/api',origin='http://localhost:3000';requireDevTarget(base);
const output='output/playwright/lumi-chat-iteration',captureOnly=process.env.E2E_CHAT_CAPTURE_ONLY==='true';
const request=createTestFetch(base),checks=[],faults=[],captures=[],streams=[];await mkdir(output,{recursive:true});
const docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function sql(query){return execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:query,encoding:'utf8'}).trim();}
const hex=text=>`CONVERT(0x${Buffer.from(text).toString('hex')} USING utf8mb4)`;
function pass(text){checks.push(text);console.log('[PASS] '+text);}
async function api(path,method='GET',body,token){const response=await request(base+path,{method,headers:{'Content-Type':'application/json','X-Idempotency-Key':randomUUID(),'X-Bypass-Rate-Limit':'true',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:response.status,...await response.json()};}
async function ok(path,method,body,token){const result=await api(path,method,body,token);assert.equal(result.status,200,path+': '+JSON.stringify(result));return result.data;}
async function account(label){return ok('/auth/register','POST',{username:`chi_${stamp}_${label}`,displayName:`${marker} ${label}`,password:'Password123!'});}
const owner=await account('owner'),peer=await account('peer'),fixture=await account('fixture');
const owned=await ok('/lumi/chat','GET',null,owner.token),other=await ok('/lumi/chat','GET',null,peer.token),sample=await ok('/lumi/chat','GET',null,fixture.token);
const {chromium}=await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);const browser=await chromium.launch({channel:'chrome',headless:true});
async function context(actor){const c=await browser.newContext({viewport:{width:1440,height:1000},permissions:['clipboard-read','clipboard-write'],reducedMotion:'reduce'});await c.addInitScript(token=>localStorage.setItem('luminous_token',token),actor.token);const p=await c.newPage();p.on('pageerror',error=>faults.push(error.message));return {c,p};}
async function send(page,text){await page.getByLabel('发给 Lumi 的消息').fill(text);const response=page.waitForResponse(r=>r.url().endsWith('/api/lumi/chat/messages')&&r.request().method()==='POST');await page.getByRole('button',{name:'发送',exact:true}).click();const result=await response;assert.equal(result.status(),200);return (await result.json()).data;}
async function terminal(actor,id){for(let index=0;index<80;index++){const page=await ok('/lumi/chat','GET',null,actor.token);const turn=page.turns.find(t=>t.id===id);if(turn&&!['PENDING','RUNNING','STOPPING'].includes(turn.status))return turn;await sleep(500);}throw new Error('聊天完成超时');}
async function observe(actor,id){
  const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),40000);const rows=[];
  try{
    const response=await fetch(origin+`/api/lumi/chat/messages/${id}/stream`,{signal:controller.signal,headers:{Authorization:`Bearer ${actor.token}`,Accept:'text/event-stream, application/json'}});assert.equal(response.status,200);assert.match(response.headers.get('content-type'),/text\/event-stream/);assert.match(response.headers.get('cache-control'),/(?:^|,\s*)no-store(?:,|$)/);
    const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
    while(true){const {value,done}=await reader.read();if(done)break;buffer+=decoder.decode(value,{stream:true}).replace(/\r/g,'');let end;
      while((end=buffer.indexOf('\n\n'))>=0){const event=buffer.slice(0,end);buffer=buffer.slice(end+2);if(!event.includes('event:turn'))continue;const data=JSON.parse(event.split('\n').find(line=>line.startsWith('data:')).slice(5));rows.push(data);if(!['PENDING','RUNNING','STOPPING'].includes(data.status)){await reader.cancel();return rows;}}
    }
    return rows;
  }finally{clearTimeout(timeout);controller.abort();}
}
async function capture(page,name){
  await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.images].map(img=>img.decode().catch(()=>{})));await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));window.scrollTo(0,0);});
  const metrics=await page.evaluate(()=>{const input=document.querySelector('#lumi-chat-input'),button=document.querySelector('.lumi-chat-composer button'),box=input.getBoundingClientRect(),submit=button.getBoundingClientRect();return {overflow:document.documentElement.scrollWidth>innerWidth,inputBottom:box.bottom,buttonBottom:submit.bottom,height:innerHeight,inputFont:getComputedStyle(input).fontSize};});
  assert.equal(metrics.overflow,false);assert.ok(metrics.buttonBottom<=metrics.height);assert.equal(metrics.inputFont,'16px');
  await page.screenshot({path:`${output}/${name}.png`,fullPage:true});captures.push({name,...metrics});
}
try{
  if(!captureOnly){
    assert.equal((await api('/lumi/chat')).status,401);
    const live=await context(owner);await live.p.goto(origin+'/lumi');await live.p.getByRole('heading',{name:'有什么想和我聊聊的？'}).waitFor();
    const turn=await send(live.p,marker+' 我的暗号是晨光蓝。请记住。用Markdown解释如何整理学习笔记：一个二级标题、三条每条约40字的建议、一个三行Java代码围栏和一个两行表格，最后写“已记住晨光蓝”。全文约200字。');
    const observed=observe(owner,turn.id);await live.p.locator(`[data-chat-turn="${turn.id}"] [data-chat-answer]`).waitFor();
    await live.p.reload();await live.p.locator(`[data-chat-turn="${turn.id}"] [data-chat-answer]`).waitFor();
    const frames=await observed;streams.push({id:turn.id,frames:frames.length,partial:frames.filter(t=>t.status==='RUNNING'&&t.answer).length});assert.ok(frames.some(t=>t.status==='RUNNING'&&t.answer));assert.equal(frames.at(-1).status,'SUCCEEDED');
    assert.equal(sql(`SELECT COUNT(*) FROM lumi_attempts WHERE task_id=${turn.id}`),'1');
    const foreign=await fetch(origin+`/api/lumi/chat/messages/${turn.id}/stream`,{headers:{Authorization:`Bearer ${peer.token}`,Accept:'text/event-stream, application/json'}});assert.equal(foreign.status,404);
    await live.p.getByRole('button',{name:'复制回答',exact:true}).waitFor();
    pass('真实DeepSeek流式快照经前端代理到达；中途刷新恢复且仅一次调用，他人订阅404');
    const stopped=await send(live.p,marker+' 请写一篇约500字的中文小故事，每段用Markdown小标题，主题是猫猫在星际旅行中整理笔记。');
    await live.p.locator(`[data-chat-turn="${stopped.id}"] [data-chat-answer]`).waitFor();await live.p.getByRole('button',{name:'停止生成',exact:true}).click();
    const ended=await terminal(owner,stopped.id);assert.equal(ended.status,'STOPPED');assert.ok(ended.answer);
    await live.p.reload();await live.p.getByText('已停止',{exact:true}).waitFor();assert.equal(sql(`SELECT COUNT(*) FROM lumi_attempts WHERE task_id=${stopped.id} AND status='SETTLED'`),'1');
    const answer=await send(live.p,marker+' 我的暗号是什么？只写暗号。');assert.match((await terminal(owner,answer.id)).answer,/晨光蓝/);
    assert.equal((await api(`/lumi/chat/messages/${stopped.id}/stop`,'POST',{},peer.token)).status,404);
    pass('生成中停止取消上游并保留部分回答；刷新可见；费用只结算一次，后续可继续追问');await live.c.close();
    const failedId=(8600000000000000000n+BigInt(stamp)*10n).toString();
    sql(`INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,answer,status,message,quota_day,accepted_at,created_at,updated_at,attempts,revision) VALUES(${failedId},${peer.user.id},${other.sessionId},'E2E_TEST_retry_${stamp}',${hex(marker+' 只写你好')},${hex(marker+' 已保存部分')},'FAILED','连接中断，已保留生成内容',DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 SECOND),UTC_TIMESTAMP(),UTC_TIMESTAMP(),1,4)`);
    const failed=await context(peer);await failed.p.goto(origin+'/lumi');await failed.p.getByRole('button',{name:'重试回答',exact:true}).waitFor();await failed.p.getByRole('button',{name:'重试回答',exact:true}).click();const retried=await terminal(peer,failedId);assert.equal(retried.status,'SUCCEEDED');assert.equal((await ok('/lumi/chat','GET',null,peer.token)).turns.length,1);await failed.c.close();
    pass('带部分回答的失败夹具可手动重试，真实模型恢复同一消息且清除旧部分回答');
  }
  const visual=await context(fixture);await visual.p.goto(origin+'/lumi');const input=visual.p.getByLabel('发给 Lumi 的消息');await input.waitFor();
  await input.fill(marker+' 尚未发送的草稿\n第二行\n第三行\n第四行\n第五行');const expanded=await input.evaluate(el=>el.getBoundingClientRect().height);assert.ok(expanded>72);await visual.p.reload();await input.waitFor();assert.match(await input.inputValue(),/尚未发送的草稿/);
  await visual.p.route('**/api/lumi/chat/messages',route=>route.abort('failed'));await visual.p.getByRole('button',{name:'发送',exact:true}).click();await visual.p.getByRole('alert').filter({hasText:'网络连接中断'}).waitFor();
  const pendingKey=await visual.p.evaluate(()=>JSON.parse(Object.keys(sessionStorage).filter(key=>key.startsWith('luminous:lumi-draft:')).map(key=>sessionStorage.getItem(key))[0]).pending.key);
  await visual.p.reload();await input.waitFor();assert.match(await input.inputValue(),/尚未发送的草稿/);assert.ok(await visual.p.evaluate(key=>Object.keys(sessionStorage).some(name=>sessionStorage.getItem(name)?.includes(key)),pendingKey));await visual.p.unroute('**/api/lumi/chat/messages');
  pass('输入框随内容增高；草稿刷新恢复；发送网络失败后保留文字和同一操作标识');
  await input.fill('');
  const id=(8700000000000000000n+BigInt(stamp)*10n).toString();
  const markdown='## 把灵感留住\n\n这是标记为 [E2E_TEST] 的排版样例。**先记录，再整理**，不用一开始就追求完整。\n\n1. 写下一句核心想法。\n2. 用 `标签` 连接相关内容。\n\n```java\nString note = "晨光蓝";\nSystem.out.println(note);\n```\n\n| 阶段 | 做法 |\n| --- | --- |\n| 记录 | 随手写下 |\n| 回顾 | 每周整理 |\n\n[阅读说明](https://example.com)\n\n<script>window.__lumiUnsafe=true</script>\n<img src="https://invalid.example/tracker" onerror="window.__lumiUnsafe=true">\n![远程图片](https://invalid.example/tracker)\n[危险链接](javascript:alert(1))';
  sql(`INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,answer,status,message,quota_day,accepted_at,created_at,updated_at,revision) VALUES(${id},${fixture.user.id},${sample.sessionId},'E2E_TEST_visual_${stamp}',${hex(marker+' 展示样例：怎样整理笔记？')},${hex(markdown)},'SUCCEEDED','Lumi 已回复',DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 SECOND),UTC_TIMESTAMP(),UTC_TIMESTAMP(),2)`);
  await visual.p.reload();const answer=visual.p.locator(`[data-chat-turn="${id}"]`);await answer.getByRole('button',{name:'复制代码',exact:true}).waitFor();
  assert.equal(await answer.locator('table').count(),1);assert.equal(await answer.locator('ol li').count(),2);assert.equal(await answer.locator('img[src*="invalid.example"]').count(),0);assert.equal(await answer.locator('script').count(),0);assert.equal(await visual.p.evaluate(()=>!!window.__lumiUnsafe),false);assert.equal(await answer.locator('a[href^="javascript:"]').count(),0);
  await answer.getByRole('button',{name:'复制代码',exact:true}).click();assert.equal((await visual.p.evaluate(()=>navigator.clipboard.readText())).replace(/\r\n/g,'\n'),'String note = "晨光蓝";\nSystem.out.println(note);');await answer.getByRole('button',{name:'复制回答',exact:true}).click();assert.equal((await visual.p.evaluate(()=>navigator.clipboard.readText())).replace(/\r\n/g,'\n'),markdown);
  pass('Markdown标题、列表、表格和代码正确；复制可用；原始HTML、脚本、远程图片和危险链接未执行');
  await capture(visual.p,'desktop');await visual.p.setViewportSize({width:768,height:1024});await capture(visual.p,'tablet');await visual.p.setViewportSize({width:390,height:844});await capture(visual.p,'mobile');
  await visual.p.evaluate(()=>document.documentElement.classList.add('dark'));await capture(visual.p,'mobile-dark');await visual.p.evaluate(()=>document.documentElement.classList.remove('dark'));
  sql(`UPDATE lumi_chat_turns SET status='FAILED',attempts=1,message='连接中断，已保留生成内容，可重试',revision=revision+1 WHERE id=${id}`);await visual.p.reload();await visual.p.getByRole('button',{name:'重试回答',exact:true}).waitFor();await capture(visual.p,'mobile-partial-failure');
  sql(`UPDATE lumi_chat_turns SET status='RUNNING',lease_token='E2E_TEST_visual',lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR),message='Lumi 正在回答',revision=revision+1 WHERE id=${id}`);await visual.p.reload();await visual.p.getByRole('button',{name:'停止生成',exact:true}).waitFor();await capture(visual.p,'mobile-streaming-fixture');
  sql(`UPDATE lumi_chat_turns SET status='STOPPED',message='已停止',lease_until=NULL,revision=revision+1 WHERE id=${id}`);await visual.p.reload();await visual.p.getByText('已停止',{exact:true}).waitFor();await visual.p.setViewportSize({width:1440,height:1000});await capture(visual.p,'desktop-stopped');await visual.p.setViewportSize({width:390,height:844});
  await visual.p.evaluate(()=>{Object.defineProperties(window.visualViewport,{height:{configurable:true,get:()=>480},offsetTop:{configurable:true,get:()=>0}});window.dispatchEvent(new Event('resize'));});await visual.p.waitForFunction(()=>document.documentElement.classList.contains('lumi-keyboard'));
  const buttonBottom=await visual.p.locator('.lumi-chat-composer button').evaluate(el=>el.getBoundingClientRect().bottom);assert.ok(buttonBottom<=480);assert.equal(await visual.p.locator('.mobile-nav').isVisible(),false);await capture(visual.p,'mobile-keyboard-simulated');
  await visual.p.reload();await input.waitFor();await input.fill(marker+' 清空时一并删除草稿');await visual.p.getByRole('button',{name:'新对话',exact:true}).click();await visual.p.getByRole('button',{name:'开始新对话',exact:true}).click();await visual.p.getByRole('heading',{name:'有什么想和我聊聊的？'}).waitFor();assert.equal(await input.inputValue(),'');
  assert.equal(await visual.p.evaluate(()=>Object.keys(sessionStorage).filter(key=>key.startsWith('luminous:lumi-draft:')).length),0);
  pass('电脑、平板、手机及深色无横向溢出；模拟键盘缩小可视区域后输入与停止/发送按钮可见；新对话清草稿');
  await visual.c.close();
  assert.deepEqual(faults,[]);
  const usage=JSON.parse(sql(`SELECT JSON_OBJECT('attempts',COUNT(*),'inputTokens',COALESCE(SUM(a.input_tokens),0),'outputTokens',COALESCE(SUM(a.output_tokens),0),'chargedMicros',COALESCE(SUM(a.charged_micros),0)) FROM lumi_attempts a JOIN lumi_chat_turns t ON t.id=a.task_id WHERE t.user_id IN (${owner.user.id},${peer.user.id},${fixture.user.id})`));
  if(!captureOnly)assert.equal(usage.attempts,4);
  await writeFile(`${output}/result.json`,JSON.stringify({marker,captureOnly,checks,streams,captures,usage,faults,keyboardEvidence:'Chrome emulated mobile viewport and synthesized visualViewport resize; no physical keyboard/device proof'},null,2));
}finally{await browser.close();}
