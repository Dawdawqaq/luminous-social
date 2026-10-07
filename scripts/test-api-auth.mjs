import net from 'node:net';
import { randomUUID } from 'node:crypto';

/** 测试认证夹具只允许独立开发入口，禁止向生产和共享基础设施发送测试流量。 */
export function requireDevTarget(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
      || !['8080', '18080'].includes(url.port) || url.username || url.password || url.pathname !== '/api') {
    throw new Error('测试目标必须是本机独立开发入口 http://localhost:8080/api 或 :18080/api');
  }
  return url;
}

/** 仅查询本次浏览器会话的挑战，不修改 Redis；网络帧可能分片，必须完整收齐 RESP 响应。 */
export async function readCaptchaOffset(session, challengeId, settings = {}) {
  if (!/^[a-f0-9]{32}$/.test(session) || !/^[a-f0-9]{32}$/.test(challengeId)) throw new Error('测试挑战标识无效');
  const port = Number(settings.port ?? process.env.E2E_REDIS_PORT ?? 63797);
  if (!Number.isInteger(port) || port < 1024 || port > 65535 || [6379, 63790].includes(port)) {
    throw new Error('验证码夹具不能连接共享或生产 Redis；请配置 E2E_REDIS_PORT 为独立测试端口');
  }
  const password = settings.password ?? process.env.E2E_REDIS_PASSWORD;
  const username = settings.username ?? process.env.E2E_REDIS_USERNAME;
  const command = values => Buffer.from(`*${values.length}\r\n` + values.map(value => `$${Buffer.byteLength(value)}\r\n${value}\r\n`).join(''));
  const query = ['GET', `luminous:captcha:{${session}}:challenge:${challengeId}`];
  const commands = password ? [(username ? ['AUTH', username, password] : ['AUTH', password]), query] : [query];
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: '127.0.0.1', port });
    let pending = Buffer.alloc(0), received = 0, settled = false;
    const finish = (error, value) => {
      if (settled) return; settled = true; socket.destroy();
      if (error) reject(error); else resolve(value);
    };
    socket.setTimeout(3000, () => finish(new Error('测试 Redis 读取超时')));
    socket.once('error', () => finish(new Error('测试 Redis 不可读，请配置独立端口及可选 E2E_REDIS_USERNAME/PASSWORD')));
    socket.once('end', () => { if (!settled) finish(new Error('测试 Redis 响应不完整')); });
    socket.once('connect', () => socket.write(Buffer.concat(commands.map(command))));
    socket.on('data', chunk => {
      pending = Buffer.concat([pending, chunk]);
      while (true) {
        const end = pending.indexOf('\r\n'); if (end < 0) return;
        const type = String.fromCharCode(pending[0]); const line = pending.subarray(1, end).toString();
        let consumed = end + 2, value = line;
        if (type === '$') {
          const length = Number(line);
          if (length === -1) { finish(new Error('测试挑战已失效，检查 API 是否连接同一个独立 Redis')); return; }
          if (!Number.isInteger(length) || length < 0 || length > 256) { finish(new Error('测试挑战响应无效')); return; }
          if (pending.length < consumed + length + 2) return;
          value = pending.subarray(consumed, consumed + length).toString(); consumed += length + 2;
        } else if (type === '-') { finish(new Error('测试 Redis 拒绝认证或读取')); return; }
        else if (type !== '+') { finish(new Error('测试 Redis 响应类型无效')); return; }
        pending = pending.subarray(consumed); received++;
        if (received === commands.length) {
          const match = /^([0-9]+)\|(login|register)$/.exec(value);
          if (!match) { finish(new Error('测试挑战数据格式无效')); return; }
          finish(null, Number(match[1])); return;
        }
      }
    });
  });
}

/** 保留正式验证码及幂等接口，仅在开发测试夹具读取答案；不向生产服务增加免验证开关。 */
export function createTestFetch(baseUrl, rawFetch = globalThis.fetch, answerReader = readCaptchaOffset) {
  const base = requireDevTarget(baseUrl);
  return async (input, options = {}) => {
    const target = new URL(input);
    if (target.origin !== base.origin || !target.pathname.startsWith('/api/')) throw new Error('测试请求超出独立 API 范围');
    const headers = new Headers(options.headers);
    const method = (options.method || 'GET').toUpperCase();
    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method) && !target.pathname.startsWith('/api/auth/captcha') && !headers.has('X-Idempotency-Key')) {
      headers.set('X-Idempotency-Key', randomUUID());
    }
    // 限流突发场景故意不带开发 bypass 头，因此不额外制造挑战请求干扰统计。
    if (method === 'POST' && ['/api/auth/login', '/api/auth/register'].includes(target.pathname)
        && headers.get('X-Bypass-Rate-Limit') === 'true' && !headers.has('X-Captcha-Proof')) {
      const purpose = target.pathname.endsWith('/register') ? 'register' : 'login';
      const challengeResponse = await rawFetch(`${baseUrl}/auth/captcha?purpose=${purpose}`, { headers: { 'X-Bypass-Rate-Limit': 'true' } });
      const challenge = await challengeResponse.json();
      if (!challengeResponse.ok || !challenge.success) throw new Error('测试验证码挑战获取失败');
      const cookies = challengeResponse.headers.get('set-cookie') || '';
      const session = /luminous_captcha_session=([a-f0-9]{32})/.exec(cookies)?.[1];
      if (!session) throw new Error('测试验证码浏览器会话缺失');
      const cookie = `luminous_captcha_session=${session}`;
      const offset = await answerReader(session, challenge.data.challengeId);
      const verifiedResponse = await rawFetch(`${baseUrl}/auth/captcha/verify`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: cookie, 'X-Bypass-Rate-Limit': 'true' },
        body: JSON.stringify({ purpose, challengeId: challenge.data.challengeId, offset, durationMillis: 400 }),
      });
      const verified = await verifiedResponse.json();
      if (!verifiedResponse.ok || !verified.success) throw new Error('测试验证码服务端验证失败');
      headers.set('Cookie', cookie); headers.set('X-Captcha-Proof', verified.data.proof);
    }
    return rawFetch(input, { ...options, headers });
  };
}
