/** 合并刷新请求；已读操作后的刷新排队执行，旧用户或旧轮询结果不能覆盖新状态。 */
export class UnreadCounter {
  private identity = "";
  private generation = 0;
  private count = 0;
  private pending?: Promise<void>;
  private dirty = false;
  private listeners = new Set<() => void>();

  constructor(private fetchCount: () => Promise<number>) {}

  getSnapshot = () => this.count;
  getIdentity = () => this.identity;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  activate(identity: string) {
    if (identity === this.identity) return;
    this.identity = identity;
    this.generation++;
    this.pending = undefined;
    this.dirty = false;
    this.publish(0);
  }

  refresh = (): Promise<void> => {
    if (!this.identity) return Promise.resolve();
    this.dirty = true;
    if (this.pending) return this.pending;
    const generation = this.generation;
    const perform = async () => {
      do {
        this.dirty = false;
        let count: number;
        try {
          count = await this.fetchCount();
        } catch {
          if (generation === this.generation && this.dirty) continue;
          return;
        }
        if (generation !== this.generation) return;
        // 已读操作触发了更新时，先完成后续查询，不能发布这次旧结果。
        if (!this.dirty && Number.isSafeInteger(count) && count >= 0)
          this.publish(count);
      } while (generation === this.generation && this.dirty);
    };
    const pending = perform().finally(() => {
      if (this.pending === pending) this.pending = undefined;
    });
    this.pending = pending;
    return pending;
  };

  private publish(count: number) {
    this.count = count;
    this.listeners.forEach((listener) => listener());
  }
}
