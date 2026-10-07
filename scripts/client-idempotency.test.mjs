import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomBytes, webcrypto } from 'node:crypto';
import { OperationCoordinator, operationFingerprint, operationGroup } from '../backend/target/client-tests/api/operationCoordinator.js';
import { randomOperationKey, sha256 } from '../backend/target/client-tests/api/browserCrypto.js';

const memoryStorage = () => {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
};
const fingerprint = 'a'.repeat(64);

async function withoutSecureContext(action) {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: {
    getRandomValues: webcrypto.getRandomValues.bind(webcrypto),
  } });
  try { await action(); }
  finally {
    if (original) Object.defineProperty(globalThis, 'crypto', original);
    else delete globalThis.crypto;
  }
}

test('HTTP 兼容摘要与原生 SHA-256 一致，覆盖填充边界、中文和大文件', async () => {
  await withoutSecureContext(async () => {
    const vectors = [new TextEncoder().encode('abc'), new TextEncoder().encode('[E2E_TEST] 中文与表情🙂'),
      ...[0, 1, 55, 56, 63, 64, 65, 119, 120, 127, 128, 129, 1024, 1024 * 1024].map(size => randomBytes(size))];
    for (const bytes of vectors) {
      assert.equal(await sha256(bytes), createHash('sha256').update(bytes).digest('hex'));
    }
    assert.equal(await sha256(new TextEncoder().encode('abc')),
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });
});

test('缺少 subtle 和 randomUUID 的 HTTP 页面仍能生成指纹及安全操作标识', async () => {
  const native = await operationFingerprint('user', '/posts', 'POST', '{"content":"[E2E_TEST] 内容"}');
  await withoutSecureContext(async () => {
    assert.equal(await operationFingerprint('user', '/posts', 'POST', '{"content":"[E2E_TEST] 内容"}'), native);
    const keys = new Set(Array.from({ length: 1000 }, randomOperationKey));
    assert.equal(keys.size, 1000);
    for (const key of keys) assert.match(key, /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
    const coordinator = new OperationCoordinator(memoryStorage());
    await coordinator.run(fingerprint, async key => assert.ok(keys.has(key) === false));
  });
});

test('缺少安全随机数时拒绝生成操作标识', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: undefined });
  try { assert.throws(randomOperationKey, /安全操作标识/); }
  finally { if (original) Object.defineProperty(globalThis, 'crypto', original); else delete globalThis.crypto; }
});

test('收藏、关注、社区成员和封禁的 POST DELETE POST 切换执行三次真实状态更新', async () => {
  for (const endpoint of ['/posts/123/bookmark', '/users/E2E_TEST_user/follow', '/communities/e2e-test/join', '/admin/users/E2E_TEST_user/ban']) {
    const coordinator = new OperationCoordinator(memoryStorage(), () => 1000);
    const group = await operationGroup('review-user', endpoint);
    const responses = new Map(), keys = [];
    let active = false;
    for (const method of ['POST', 'DELETE', 'POST']) {
      const requestFingerprint = await operationFingerprint('review-user', endpoint, method, undefined);
      const result = await coordinator.run(requestFingerprint, async key => {
        keys.push(key);
        if (responses.has(key)) return responses.get(key);
        active = method === 'POST';
        const response = { active };
        responses.set(key, response);
        return response;
      }, group);
      assert.equal(result.active, method === 'POST');
      assert.equal(active, result.active);
    }
    assert.equal(new Set(keys).size, 3);
  }
  assert.notEqual(await operationGroup('user', '/posts/1/bookmark'), await operationGroup('user', '/posts/2/bookmark'));
  assert.notEqual(await operationGroup('user', '/posts/1/bookmark'), await operationGroup('other', '/posts/1/bookmark'));
});
test('并发重复点击只调用一次执行函数', async () => {
  const coordinator = new OperationCoordinator(memoryStorage());
  let calls = 0, release;
  const gate = new Promise(resolve => { release = resolve; });
  const execute = async () => { calls++; await gate; return { id: 1 }; };
  const requests = Array.from({ length: 50 }, () => coordinator.run(fingerprint, execute));
  release();
  assert.equal((await Promise.all(requests)).length, 50);
  assert.equal(calls, 1);
});
test('超时与刷新后重试复用操作标识，会话存储不保存响应', async () => {
  const storage = memoryStorage();
  let original;
  await assert.rejects(new OperationCoordinator(storage).run(fingerprint, async key => {
    original = key; throw new Error('超时');
  }));
  await new OperationCoordinator(storage).run(fingerprint, async key => {
    assert.equal(key, original); return { token: '禁止写入会话存储' };
  });
  assert.equal(storage.getItem('luminous_operation_keys').includes('禁止写入'), false);
});
test('成功后的连击复用标识，新操作和不同参数独立', async () => {
  let time = 1000;
  const coordinator = new OperationCoordinator(memoryStorage(), () => time);
  const keys = [];
  const execute = async key => { keys.push(key); return 1; };
  await coordinator.run(fingerprint, execute);
  time += 100;
  await coordinator.run(fingerprint, execute);
  await coordinator.run('b'.repeat(64), execute);
  time += 2001;
  await coordinator.run(fingerprint, execute);
  assert.equal(keys[0], keys[1]);
  assert.notEqual(keys[1], keys[2]);
  assert.notEqual(keys[0], keys[3]);
});
test('确定失败允许新尝试，不确定的五百错误继续使用原标识', async () => {
  const coordinator = new OperationCoordinator(memoryStorage());
  const keys = [];
  const fail = status => coordinator.run(fingerprint, async key => {
    keys.push(key); throw Object.assign(new Error('失败'), { status });
  });
  await assert.rejects(fail(503)); await assert.rejects(fail(503));
  await assert.rejects(fail(400)); await assert.rejects(fail(400));
  assert.equal(keys[0], keys[1]); assert.equal(keys[1], keys[2]); assert.notEqual(keys[2], keys[3]);
});
test('请求指纹绑定用户、接口、参数和凭证，JSON 键顺序不影响同一操作', async () => {
  const first = await operationFingerprint('user', '/posts', 'POST', '{"a":1,"b":2}', 'proof');
  assert.equal(first, await operationFingerprint('user', '/posts', 'POST', '{"b":2,"a":1}', 'proof'));
  for (const input of [['other','/posts','POST','{"a":1,"b":2}','proof'],['user','/votes','POST','{"a":1,"b":2}','proof'],['user','/posts','POST','{"a":2,"b":2}','proof'],['user','/posts','POST','{"a":1,"b":2}','other']]) {
    assert.notEqual(first, await operationFingerprint(...input));
  }
});

test('点赞、取消、再次点赞属于不同业务操作，不能重放旧点赞结果', async () => {
  const coordinator = new OperationCoordinator(memoryStorage());
  const keys = []; const execute = async key => { keys.push(key); return 1; };
  await coordinator.run(fingerprint, execute, 'vote-resource');
  await coordinator.run('b'.repeat(64), execute, 'vote-resource');
  await coordinator.run(fingerprint, execute, 'vote-resource');
  assert.equal(new Set(keys).size, 3);
});

 test('全部已读确认后新通知可触发新操作，在途连击合并且超时继续复用', async () => {
  const coordinator = new OperationCoordinator(memoryStorage());
  const keys=[];let release;
  const gate=new Promise(resolve=>{release=resolve;});
  const execute=async key=>{keys.push(key);await gate;return 1;};
  const first=coordinator.run(fingerprint,execute,fingerprint,false);
  const duplicate=coordinator.run(fingerprint,execute,fingerprint,false);
  assert.equal(first,duplicate);release();await first;
  await coordinator.run(fingerprint,async key=>{keys.push(key);return 1;},fingerprint,false);
  assert.notEqual(keys[0],keys[1]);
  await assert.rejects(coordinator.run(fingerprint,async key=>{keys.push(key);throw new Error('超时');},fingerprint,false));
  await coordinator.run(fingerprint,async key=>{assert.equal(key,keys[2]);return 1;},fingerprint,false);
 });
