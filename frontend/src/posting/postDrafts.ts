export interface PostDraft { community: string; title: string; content: string; image: string; file: File | null; updatedAt: number }
const writes = new Map<string, Promise<void>>();

/** IndexedDB 同时保存文字和未上传文件；键含账号与社区，避免跨账号或跨入口覆盖。 */
async function database() {
  return new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open("luminous-post-drafts", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("drafts");
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
export async function readPostDraft(key: string): Promise<PostDraft | undefined> {
  await writes.get(key)?.catch(() => {});
  const db = await database();
  try {
    return await new Promise((resolve, reject) => {
      const request = db.transaction("drafts").objectStore("drafts").get(key);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } finally { db.close(); }
}
async function writePostDraft(key: string, draft?: PostDraft) {
  const db = await database();
  try {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction("drafts", "readwrite");
      const store = tx.objectStore("drafts");
      if (draft) store.put(draft, key); else store.delete(key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally { db.close(); }
}
export function savePostDraft(key: string, draft?: PostDraft) {
  const next = (writes.get(key) || Promise.resolve()).catch(() => {}).then(() => writePostDraft(key, draft));
  writes.set(key, next);
  void next.finally(() => { if (writes.get(key) === next) writes.delete(key); }).catch(() => {});
  return next;
}
