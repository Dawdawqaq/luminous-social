import test from 'node:test';
import assert from 'node:assert/strict';
import { UnreadCounter } from '../backend/target/client-tests/notification/UnreadCounter.js';
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return {promise,resolve}; };
test('收到通知与已读后刷新更新同一未读状态',async()=>{
  let value=3;const counter=new UnreadCounter(async()=>value);counter.activate('user');
  await counter.refresh();assert.equal(counter.getSnapshot(),3);
  value=0;await counter.refresh();assert.equal(counter.getSnapshot(),0);
  value=1;await counter.refresh();assert.equal(counter.getSnapshot(),1);
});
test('并发刷新合并且旧轮询不能覆盖已读后的结果',async()=>{
  const first=deferred();let calls=0;
  const counter=new UnreadCounter(async()=>{calls++;return calls===1?first.promise:0;});counter.activate('user');
  const polling=counter.refresh();const afterRead=counter.refresh();
  assert.equal(polling,afterRead);first.resolve(5);await afterRead;
  assert.equal(calls,2);assert.equal(counter.getSnapshot(),0);
});
test('退出和切换账号后丢弃在途的旧用户结果',async()=>{
  const old=deferred();let calls=0;
  const counter=new UnreadCounter(async()=>++calls===1?old.promise:2);counter.activate('old');const first=counter.refresh();
  counter.activate('');assert.equal(counter.getSnapshot(),0);
  counter.activate('new');await counter.refresh();old.resolve(100);await first;
  assert.equal(counter.getSnapshot(),2);
});
test('网络失败保留已确认计数，后续轮询可以恢复',async()=>{
  let failed=false;const counter=new UnreadCounter(async()=>{if(failed)throw new Error('故障');return 4;});counter.activate('user');
  await counter.refresh();failed=true;await counter.refresh();assert.equal(counter.getSnapshot(),4);
  failed=false;await counter.refresh();assert.equal(counter.getSnapshot(),4);
});
