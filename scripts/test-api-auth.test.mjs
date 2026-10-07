import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { createTestFetch, readCaptchaOffset, requireDevTarget } from './test-api-auth.mjs';

test('拒绝生产、共享或远程测试入口', () => {
  for (const url of ['http://localhost/api','https://localhost/api','http://localhost:8088/api','http://example.com:8080/api']) {
    assert.throws(() => requireDevTarget(url));
  }
  assert.equal(requireDevTarget('http://localhost:18080/api').port,'18080');
});
test('测试夹具获取挑战后走正式验证及注册接口，写操作携带独立 key', async () => {
  const calls=[];
  const fake=async (url,options={}) => {
    calls.push({url,options});
    if(url.includes('/captcha?')) return new Response(JSON.stringify({success:true,data:{challengeId:'b'.repeat(32)}}),{headers:{'set-cookie':`luminous_captcha_session=${'a'.repeat(32)}; HttpOnly`}});
    if(url.endsWith('/captcha/verify')) return new Response(JSON.stringify({success:true,data:{proof:'c'.repeat(32)}}));
    return new Response(JSON.stringify({success:true,data:{id:'[E2E_TEST]'}}));
  };
  const fetch=createTestFetch('http://localhost:8080/api',fake,async(session,id) => {
    assert.equal(session,'a'.repeat(32));assert.equal(id,'b'.repeat(32));return 123;
  });
  await fetch('http://localhost:8080/api/auth/register',{method:'POST',headers:{'X-Bypass-Rate-Limit':'true'},body:'{}'});
  assert.equal(calls.length,3); assert.equal(JSON.parse(calls[1].options.body).offset,123);
  assert.equal(calls[2].options.headers.get('X-Captcha-Proof'),'c'.repeat(32));
  assert.equal(calls[2].options.headers.get('Cookie'),`luminous_captcha_session=${'a'.repeat(32)}`);
  assert.ok(calls[2].options.headers.get('X-Idempotency-Key'));
});
test('限流测量不额外制造验证码请求，显式重试 key 保留', async () => {
  const calls=[];
  const fetch=createTestFetch('http://localhost:8080/api',async(url,options) => {calls.push(options);return new Response('{}');},async()=>{throw new Error('不能调用');});
  await fetch('http://localhost:8080/api/auth/login',{method:'POST',headers:{'X-Idempotency-Key':'original-key'}});
  assert.equal(calls.length,1);assert.equal(calls[0].headers.get('X-Idempotency-Key'),'original-key');
});
test('读取挑战正确处理带认证和网络分片的 RESP，只查询本次键', async () => {
  const session='a'.repeat(32),id='b'.repeat(32);let request='';
  const server=net.createServer(socket=>{
    socket.on('data',chunk=>{
      request+=chunk.toString();
      if(request.includes(`challenge:${id}`)) {socket.write('+OK\r\n$9\r\n123|');setTimeout(()=>socket.end('login\r\n'),5);}
    });
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try {
    assert.equal(await readCaptchaOffset(session,id,{port:server.address().port,password:'[E2E_TEST] password'}),123);
    assert.ok(request.includes('AUTH'));assert.ok(request.includes(`luminous:captcha:{${session}}:challenge:${id}`));
    assert.equal(request.includes('SET'),false);
    await assert.rejects(readCaptchaOffset(session,id,{port:6379}));
    await assert.rejects(readCaptchaOffset(session,id,{port:63790}));
  } finally { await new Promise(resolve=>server.close(resolve)); }
});
