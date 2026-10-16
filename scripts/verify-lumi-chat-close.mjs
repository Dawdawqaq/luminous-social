import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
import {createTestFetch,requireDevTarget} from './test-api-auth.mjs';

const marker=process.env.E2E_RUN_MARKER;assert.match(marker||'',/^\[E2E_TEST\] lumi-chat-\d+$/);
const stamp=marker.split('lumi-chat-')[1],base='http://localhost:8080/api';requireDevTarget(base);
const request=createTestFetch(base),docker='C:/Users/zx080/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
function sql(text){return execFileSync(docker,['exec','-i','luminous-dev-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'],{input:text,encoding:'utf8'}).trim();}
async function api(path,method='GET',body,token){const response=await request(base+path,{method,headers:{'Content-Type':'application/json','X-Idempotency-Key':randomUUID(),'X-Bypass-Rate-Limit':'true',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});assert.equal(response.status,200);return (await response.json()).data;}
const actor=await api('/auth/register','POST',{username:`chclose_${stamp}`,displayName:marker,password:'Password123!'});
const page=await api('/lumi/chat','GET',null,actor.token),id=(8800000000000000000n+BigInt(stamp)).toString();
sql(`INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,answer,status,message,quota_day,accepted_at,created_at,updated_at,lease_until,lease_token) VALUES(${id},${actor.user.id},${page.sessionId},'E2E_TEST_close_${stamp}','${marker} 流连接关闭夹具','[E2E_TEST] 已保存','RUNNING','Lumi 正在回答',DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),UTC_TIMESTAMP(),UTC_TIMESTAMP(),UTC_TIMESTAMP(),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 10 MINUTE),'E2E_TEST_close')`);
for(let index=0;index<2;index++){
    const controller=new AbortController();
    const response=await fetch(`http://localhost:3000/api/lumi/chat/messages/${id}/stream`,{signal:controller.signal,headers:{Authorization:`Bearer ${actor.token}`,Accept:'text/event-stream, application/json'}});assert.equal(response.status,200);
    const reader=response.body.getReader();await reader.read();controller.abort();await reader.cancel().catch(()=>{});
    sql(`UPDATE lumi_chat_turns SET revision=revision+1 WHERE id=${id}`);await new Promise(resolve=>setTimeout(resolve,1500));
}
sql(`UPDATE lumi_chat_turns SET status='STOPPED',message='已停止',revision=revision+1 WHERE id=${id}`);
assert.equal(sql(`SELECT COUNT(*) FROM lumi_attempts WHERE task_id=${id}`),'0');
await writeFile('output/lumi-chat-close-result.json',JSON.stringify({marker,disconnects:2,providerCalls:0,fixture:'显式 RUNNING SQL 夹具，不调用模型'},null,2));
console.log('[PASS] 两次真实代理流连接主动关闭；未创建模型调用');
