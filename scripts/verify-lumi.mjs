import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { createTestFetch, requireDevTarget } from './test-api-auth.mjs';

const marker=process.env.E2E_RUN_MARKER;
assert.match(marker||'',/^\[E2E_TEST\] lumi-\d+$/);
const stamp=marker.split('lumi-')[1],base='http://localhost:8080/api',origin='http://localhost:3000',output='output/playwright/lumi';
requireDevTarget(base);
const request=createTestFetch(base),checks=[],faults=[],answers=[];
const docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
await mkdir(output,{recursive:true});
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function sql(text){return execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:text,encoding:'utf8'}).trim();}
function pass(text){checks.push(text);console.log('[PASS] '+text);}
async function api(path,method='GET',body,token,key=randomUUID()){
  const response=await request(base+path,{method,headers:{'Content-Type':'application/json','X-Bypass-Rate-Limit':'true','X-Idempotency-Key':key,...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});
  const raw=await response.text();return {status:response.status,...(raw?JSON.parse(raw):{data:null})};
}
async function ok(path,method,body,token,key){const result=await api(path,method,body,token,key);assert.equal(result.status,200,path+': '+JSON.stringify(result));return result.data;}
async function account(label){return ok('/auth/register','POST',{username:`lumi_${stamp}_${label}`,displayName:`${marker} ${label}`,password:'Password123!'});}
for(let i=0;i<50;i++){try{if((await fetch(base+'/users/profile-presets')).ok)break;}catch{}if(i===49)throw new Error('开发服务未就绪');await sleep(1000);}
const owner=await account('owner'),desktop=await account('desktop'),mobile=await account('mobile'),web=await account('web'),searcher=await account('search');
const community=await ok('/communities','POST',{slug:`e2e-lumi-${stamp}`,name:`Lumi ${marker}`,description:marker,category:'Systems'},owner.token);
const post=await ok('/posts','POST',{community:community.name,title:'[E2E_TEST]猫咪作息',content:`${marker}。本帖讨论猫咪晚饭安排。楼主明确建议每天晚上七点喂晚饭，避免睡前临时加餐。`,isPublic:true},owner.token);
const relatedId=(8300000000000000000n+BigInt(stamp)*10n).toString();
sql(`INSERT INTO posts(id,author_id,community_id,title,content,is_public,status,post_type) VALUES(${relatedId},${owner.user.id},${community.id},'[E2E_TEST]猫咪作息','${marker} 猫咪作息，讨论晚饭安排与睡前加餐。',TRUE,'NORMAL','DISCUSSION');`);
assert.equal((await api(`/lumi/posts/${post.id}/tasks`)).status,401);
assert.equal((await api('/auth/login','POST',{username:'Lumi',password:'Password123!'})).status,401);
assert.equal((await api('/auth/register','POST',{username:'lUMi',displayName:marker,password:'Password123!'})).status,409);
assert.equal((await api('/users/Lumi/follow','POST',null,owner.token)).status,400);
const bot=await ok('/users/Lumi','GET',null,owner.token);assert.equal(bot.accountType,'BOT');assert.equal(bot.growth,null);
pass('机器人账号不能登录、不能被普通注册冒用或关注；任务接口匿名不可读，资料明确标识BOT且无成长等级');
const webComment=await ok(`/posts/${post.id}/comments`,'POST',{content:'@Lumi 联网查一下这个安排'},web.token);
const webStatus=await ok(`/lumi/posts/${post.id}/tasks`,'GET',null,web.token);
assert.equal(webStatus.tasks[0].status,'REJECTED');assert.match(webStatus.tasks[0].message,/联网/);
assert.equal(sql(`SELECT COUNT(*) FROM lumi_attempts a JOIN lumi_tasks t ON t.id=a.task_id WHERE t.comment_id=${webComment.id}`),'0');
pass('未接入的联网请求明确拒绝，不调用模型、不预占成功额度');

const {chromium}=await import(pathToFileURL(process.env.E2E_PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({channel:'chrome',headless:true});
async function capture(page,name){await page.evaluate(async()=>{await document.fonts.ready;await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));});await page.screenshot({path:`${output}/${name}.png`});}
try{
  for(const [device,width,height,actor] of [['desktop',1440,1000,desktop],['mobile',390,844,mobile]]){
    const context=await browser.newContext({viewport:{width,height},reducedMotion:'reduce'});
    await context.addInitScript(token=>localStorage.setItem('luminous_token',token),actor.token);
    const page=await context.newPage();page.setDefaultTimeout(15000);page.on('pageerror',error=>faults.push(error.message));
    await page.goto(`${origin}/post/${post.id}`);
    const form=page.getByRole('form',{name:'发布评论'});
    await form.scrollIntoViewIfNeeded();await form.getByRole('button',{name:'问 Lumi',exact:true}).click();
    assert.equal(await form.getByRole('textbox').innerText(),'@Lumi ');
    await form.getByRole('textbox').fill('@Lumi 根据正文，楼主建议晚上几点给猫咪喂晚饭？一句话回答。');
    await form.evaluate(element=>element.scrollIntoView({block:'start',behavior:'instant'}));
    await page.evaluate(()=>scrollBy({top:-145,behavior:'instant'}));
    await capture(page,`compose-${device}`);
    const createdResponse=page.waitForResponse(response=>new URL(response.url()).pathname===`/api/posts/${post.id}/comments`&&response.request().method()==='POST');
    await form.getByRole('button',{name:'发表评论',exact:true}).click();
    const created=(await (await createdResponse).json()).data;
    const state=page.locator(`[data-comment-id="${created.id}"] [data-lumi-task]`);await state.waitFor();
    if(['PENDING','RUNNING'].includes(await state.getAttribute('data-lumi-task'))){await state.evaluate(element=>element.scrollIntoView({block:'center',behavior:'instant'}));await capture(page,`pending-${device}`);}
    await state.getByRole('link',{name:'查看 Lumi 回复'}).waitFor({timeout:65000});
    const status=await ok(`/lumi/posts/${post.id}/tasks`,'GET',null,actor.token);
    const task=status.tasks.find(item=>item.commentId===created.id);assert.equal(task.status,'SUCCEEDED');
    const ctx=await ok(`/posts/${post.id}/comments/${task.replyId}/context`,'GET',null,actor.token);
    assert.equal(ctx.target.author.username,'Lumi');assert.equal(ctx.target.author.accountType,'BOT');assert.equal(ctx.target.author.communityGrowth,null);
    assert.equal(ctx.target.replyToCommentId,created.id);assert.equal(ctx.target.parentId,created.id);
    assert.ok(ctx.target.content.length<=150);assert.match(ctx.target.content,/七点|7点|19[:：]00|19点/);
    assert.equal(sql(`SELECT COUNT(*) FROM comments WHERE reply_to_comment_id=${created.id} AND author_id=880000000000000001`),'1');
    const expectedCount=Number(sql(`SELECT COUNT(*) FROM comments WHERE post_id=${post.id}`));
    await page.waitForFunction(expected=>Number(document.querySelector('#responses h2')?.textContent.match(/\d+/)?.[0])===expected,expectedCount);
    answers.push({device,question:created.content,answer:ctx.target.content,length:ctx.target.content.length});
    const repliesLoaded=page.waitForResponse(response=>new URL(response.url()).pathname===`/api/posts/${post.id}/comments/${created.id}/replies`);
    await state.getByRole('link',{name:'查看 Lumi 回复'}).click();await repliesLoaded;
    const reply=page.locator(`[aria-label="楼内讨论"] [data-comment-id="${task.replyId}"]`);await reply.waitFor();await reply.scrollIntoViewIfNeeded();
    assert.ok(await reply.getByText('AI',{exact:true}).isVisible());await capture(page,`reply-${device}`);
    await page.evaluate(()=>document.documentElement.classList.add('dark'));await capture(page,`dark-${device}`);
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.evaluate(()=>document.documentElement.classList.remove('dark'));
    await page.goto(`${origin}/u/Lumi`);assert.equal(await page.getByRole('button',{name:'关注',exact:true}).count(),0);
    await context.close();pass(`${device}：真实DeepSeek问答、150字符校验、AI标识、楼内定位、深色与移动布局、Lumi主页无关注入口通过`);
  }
  const context=await browser.newContext({viewport:{width:1440,height:1000}});await context.addInitScript(token=>localStorage.setItem('luminous_token',token),web.token);
  const page=await context.newPage();page.on('pageerror',error=>faults.push(error.message));await page.goto(`${origin}/post/${post.id}`);await page.locator(`[data-comment-id="${webComment.id}"]`).scrollIntoViewIfNeeded();await page.getByText('Lumi 暂未接入联网搜索，可以先询问帖子内容',{exact:true}).waitFor();await capture(page,'rejected-desktop');
  // 仅失败状态由带标记的 SQL 夹具构造；点击重试后的任务、模型调用及回复仍使用真实链路。
  const retryComment=(await ok(`/posts/${post.id}/comments`,'POST',{content:'[E2E_TEST] 模型重试验证'},web.token)).id,retryTask=(8400000000000000000n+BigInt(stamp)*10n).toString();
  sql(`UPDATE comments SET content='@Lumi [E2E_TEST] 根据正文，猫咪晚饭几点吃？' WHERE id=${retryComment}; INSERT INTO lumi_tasks(id,comment_id,post_id,user_id,quota_day,status,accepted_at,created_at,updated_at,message) VALUES(${retryTask},${retryComment},${post.id},${web.user.id},DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),'FAILED',DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 61 SECOND),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'Lumi 暂时无法完成这条请求，请稍后重试');`);
  await page.reload();const retryState=page.locator(`[data-comment-id="${retryComment}"] [data-lumi-task]`);await retryState.waitFor();await retryState.scrollIntoViewIfNeeded();await capture(page,'failed-desktop');
  await page.setViewportSize({width:390,height:844});await retryState.scrollIntoViewIfNeeded();await capture(page,'failed-mobile');
  await page.setViewportSize({width:1440,height:1000});await retryState.getByRole('button',{name:'重试 Lumi'}).click();await retryState.getByRole('link',{name:'查看 Lumi 回复'}).waitFor({timeout:65000});
  const retryResult=(await ok(`/lumi/posts/${post.id}/tasks`,'GET',null,web.token)).tasks.find(item=>item.id===retryTask);
  assert.equal(retryResult.status,'SUCCEEDED');assert.equal(sql(`SELECT COUNT(*) FROM lumi_tasks WHERE comment_id=${retryComment}`),'1');
  pass('显式失败夹具在桌面和手机显示可重试状态；重试后真实DeepSeek完成，同一任务只产生一条回复');
  await context.close();
  const searchComment=await ok(`/posts/${post.id}/comments`,'POST',{content:'@Lumi 请用站内搜索找关于猫咪作息的其他帖子，给一篇标题和站内路径。'},searcher.token);
  let searchTask;
  for(let i=0;i<25;i++){
    searchTask=(await ok(`/lumi/posts/${post.id}/tasks`,'GET',null,searcher.token)).tasks.find(task=>task.commentId===searchComment.id);
    if(searchTask&&['SUCCEEDED','FAILED','CANCELLED','REJECTED'].includes(searchTask.status))break;
    await sleep(2500);
  }
  assert.equal(searchTask?.status,'SUCCEEDED',JSON.stringify(searchTask));
  const searched=await ok(`/posts/${post.id}/comments/${searchTask.replyId}/context`,'GET',null,searcher.token);
  assert.ok(searched.target.content.includes(`/post/${relatedId}`));assert.ok(searched.target.content.length<=150);
  assert.doesNotMatch(searched.target.content,/只找到当前|没有其他|未找到相关/);
  assert.equal(sql(`SELECT attempts FROM lumi_tasks WHERE id=${searchTask.id}`),'2');
  answers.push({device:'api-search',question:searchComment.content,answer:searched.target.content,length:searched.target.content.length});
  const searchContext=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  await searchContext.addInitScript(token=>localStorage.setItem('luminous_token',token),searcher.token);
  const searchPage=await searchContext.newPage();searchPage.on('pageerror',error=>faults.push(error.message));
  await searchPage.goto(`${origin}/post/${post.id}?comment=${searchTask.replyId}`);
  const searchedReply=searchPage.locator(`[aria-label="楼内讨论"] [data-comment-id="${searchTask.replyId}"]`);await searchedReply.waitFor();await searchedReply.scrollIntoViewIfNeeded();
  assert.equal(await searchedReply.getByRole('link',{name:'相关帖子',exact:true}).getAttribute('href'),`/post/${relatedId}`);await capture(searchPage,'search-desktop');
  await searchPage.setViewportSize({width:390,height:844});await searchedReply.scrollIntoViewIfNeeded();
  await searchedReply.evaluate(element=>window.scrollBy(0,element.getBoundingClientRect().top-280));
  const sourceBox=await searchedReply.getByRole('link',{name:'相关帖子',exact:true}).boundingBox();
  assert.ok(sourceBox&&sourceBox.y>=124&&sourceBox.y+sourceBox.height<782,'手机来源链接应完整位于固定导航之间');
  await capture(searchPage,'search-mobile');await searchContext.close();
  pass('真实模型调用受控站内搜索工具一次，再生成带已核实站内路径的短回答；两端来源链接可用，未执行联网');
}finally{await browser.close();}

for(const actor of [desktop,mobile]){
  let rows=[];for(let i=0;i<20;i++){
    rows=JSON.parse(sql(`SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('type',type,'actor',CAST(actor_id AS CHAR),'comment',CAST(comment_id AS CHAR))),'[]') FROM notifications WHERE receiver_id=${actor.user.id} AND actor_id=880000000000000001`));
    if(rows.length)break;await sleep(500);
  }
  assert.equal(rows.length,1);assert.equal(rows[0].type,'REPLY_COMMENT');
}
assert.equal(sql(`SELECT COUNT(*) FROM notifications WHERE receiver_id=${owner.user.id} AND actor_id=880000000000000001`),'0');
assert.equal(sql('SELECT COUNT(*) FROM growth_rewards WHERE user_id=880000000000000001'),'0');
assert.equal(sql('SELECT COUNT(*) FROM notifications WHERE receiver_id=880000000000000001'),'0');
const own=await ok(`/lumi/posts/${post.id}/tasks`,'GET',null,desktop.token);assert.equal(own.tasks.length,1);assert.notEqual(own.tasks[0].commentId,webComment.id);
pass('回复通知通过真实异步链路仅发送给调用者，每人一次；不通知原帖作者、不通知Lumi、不产生机器人经验，任务按账号隔离');
const plain=await ok(`/posts/${post.id}/comments`,'POST',{content:'谢谢，这个答案很清楚',replyToCommentId:own.tasks[0].replyId},owner.token);
assert.equal(sql(`SELECT COUNT(*) FROM lumi_tasks WHERE comment_id=${plain.id}`),'0');
assert.equal(sql('SELECT COUNT(*) FROM notifications WHERE receiver_id=880000000000000001'),'0');
pass('直接回复Lumi而未明确@不会触发模型或向机器人发送通知');
assert.deepEqual(faults,[]);
const usage=JSON.parse(sql(`SELECT JSON_OBJECT('attempts',COUNT(*),'inputTokens',COALESCE(SUM(a.input_tokens),0),'outputTokens',COALESCE(SUM(a.output_tokens),0),'applicationChargedMicros',COALESCE(SUM(a.charged_micros),0)) FROM lumi_attempts a JOIN lumi_tasks t ON t.id=a.task_id WHERE t.post_id=${post.id}`));
await writeFile(`${output}/result.json`,JSON.stringify({marker,checks,answers,usage,faults},null,2));
