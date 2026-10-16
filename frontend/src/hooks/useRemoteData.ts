import { useEffect, useRef, useState } from "react";
export function useRemoteData<T>(
  loader: () => Promise<T>,
  dependencies: readonly unknown[],
  initial: T,
  enabled = true,
) {
  const [data, setData] = useState(initial);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const initialRef = useRef(initial);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  useEffect(() => {
    let cancelled = false;
    setData(initialRef.current);
    setError("");
    setLoading(enabled);
    if (!enabled) return;
    loaderRef
      .current()
      .then((value) => {
        if (!cancelled) setData(value);
      })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : "加载失败，请重试");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [...dependencies, revision, enabled]);
  return {
    data,
    setData,
    loading,
    error,
    reload: () => setRevision((r) => r + 1),
  };
}
