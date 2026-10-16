import { useCallback, useEffect, useState } from "react";
import { lumiApi } from "../api/client";
import { LumiTask } from "../types";

/** 只在当前账号有进行中任务时轮询，切换帖子或账号会取消旧请求。 */
export function useLumiTasks(postId: string | number, userId?: string) {
  const [tasks, setTasks] = useState<LumiTask[]>([]);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision(value => value + 1), []);
  useEffect(() => {
    setTasks([]); setError("");
    if (!userId) return;
    const controller = new AbortController();
    let timer: number | undefined;
    let busy = false;
    const load = async () => {
      if (busy || controller.signal.aborted) return;
      busy = true;
      if (timer) clearTimeout(timer);
      try {
        const result = await lumiApi.status(postId, controller.signal);
        if (controller.signal.aborted) return;
        setTasks(result.tasks); setError("");
        if (result.enabled && result.tasks.some(task => task.status === "PENDING" || task.status === "RUNNING")) timer = window.setTimeout(() => { if (document.visibilityState === "visible") void load(); }, 3000);
      } catch (err) {
        if (!controller.signal.aborted) setError(err instanceof Error ? err.message : "Lumi 状态更新失败");
      } finally { busy = false; }
    };
    const visible = () => { if (document.visibilityState === "visible") void load(); };
    document.addEventListener("visibilitychange", visible);
    void load();
    return () => { controller.abort(); clearTimeout(timer); document.removeEventListener("visibilitychange", visible); };
  }, [postId, userId, revision]);
  return { tasks, error, refresh };
}
