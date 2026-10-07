import { randomOperationKey, sha256 } from './browserCrypto.js';

interface SavedOperation {
  key: string;
  group?: string;
  expiresAt: number;
  completedAt?: number;
}

/** 同一操作合并点击，结果未确认时保留标识，刷新后的重试也能复用。 */
export class OperationCoordinator {
  private operations = new Map<string, SavedOperation>();
  private pending = new Map<string, Promise<unknown>>();
  private readonly storageKey = 'luminous_operation_keys';

  constructor(private storage?: Pick<Storage, 'getItem' | 'setItem'>, private now = () => Date.now()) {
    try {
      const saved = JSON.parse(storage?.getItem(this.storageKey) || '[]') as [string, SavedOperation][];
      for (const [fingerprint, operation] of saved) {
        if (/^[a-f0-9]{64}$/.test(fingerprint) && typeof operation.key === 'string' && operation.expiresAt > this.now()) {
          this.operations.set(fingerprint, operation);
        }
      }
    } catch { /* 存储不可用时仍保留当前页面的内存保护。 */ }
  }

  run<T>(fingerprint: string, execute: (key: string) => Promise<T>, group = fingerprint, reuseCompleted = true): Promise<T> {
    const active = this.pending.get(fingerprint);
    if (active) return active as Promise<T>;
    const now = this.now();
    for (const [id, operation] of this.operations) {
      if (operation.expiresAt <= now || (operation.completedAt !== undefined && now - operation.completedAt > 2000)
          || (id !== fingerprint && operation.group === group && operation.completedAt !== undefined)) {
        this.operations.delete(id);
      }
    }
    let operation = this.operations.get(fingerprint);
    if (!operation) {
      if (this.operations.size >= 1000) return Promise.reject(new Error('操作繁忙，请先确认之前的提交结果'));
      operation = { key: randomOperationKey(), group, expiresAt: now + 24 * 3600 * 1000 };
      this.operations.set(fingerprint, operation);
    } else { operation.group = group; }
    this.save();
    const key = operation.key;
    const promise = Promise.resolve().then(() => execute(key)).then(result => {
      const saved = this.operations.get(fingerprint);
      // 全部已读的目标集合会随新通知变化，确认完成后下一次点击应形成新操作；失败重试仍复用。
      if (saved && reuseCompleted) saved.completedAt = this.now();
      else if (saved) this.operations.delete(fingerprint);
      this.save();
      return result;
    }).catch(error => {
      const status = (error as { status?: number }).status;
      // 明确失败的业务没有提交；处理中与不确定失败继续复用原标识。
      if (status && status >= 400 && status < 500 && status !== 409 && status !== 408) {
        this.operations.delete(fingerprint);
      }
      this.save();
      throw error;
    }).finally(() => this.pending.delete(fingerprint));
    this.pending.set(fingerprint, promise);
    return promise;
  }

  private save() {
    try { this.storage?.setItem(this.storageKey, JSON.stringify([...this.operations])); }
    catch { /* 浏览器限制持久化时当前页面仍可复用操作标识。 */ }
  }
}

/** 仅保存请求指纹和操作标识，不把密码、Token、文件内容或响应写入会话存储。 */
export async function operationFingerprint(identity: string, endpoint: string, method: string, body: BodyInit | null | undefined, proof = ''): Promise<string> {
  let content: unknown = body ?? '';
  if (body instanceof FormData) {
    const fields: unknown[] = [];
    for (const [name, value] of body.entries()) {
      fields.push(typeof value === 'string' ? [name, value] : [name, value.name, value.type, value.size,
        await hash(await value.arrayBuffer())]);
    }
    content = fields;
  } else if (typeof body === 'string') {
    try { content = canonical(JSON.parse(body)); } catch { content = body; }
  }
  return hash(new TextEncoder().encode(JSON.stringify([identity, endpoint, method, content, proof])));
}
/** 同一资源的反向操作共享分组，完成切换后不再重放切换前的成功结果。 */
export function operationGroup(identity: string, endpoint: string): Promise<string> {
  return operationFingerprint(identity, endpoint, '', undefined);
}
function canonical(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonical);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, canonical(item)]));
  }
  return value;
}
async function hash(value: Uint8Array | ArrayBuffer): Promise<string> {
  return sha256(value instanceof Uint8Array ? value : new Uint8Array(value));
}
