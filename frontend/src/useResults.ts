import { useEffect, useRef, useState } from 'react';
import { manifestSchema, snapshotSchema, snapshotUrl, type Snapshot, type SourceStatus } from './data';

const CACHE_KEY = 'brasil-decide:2026:presidente:t2:v1';
const manifestUrl = import.meta.env.VITE_RESULTS_MANIFEST_URL || '/results/manifest.json';
function restore(): Snapshot | null {
  try { const data = localStorage.getItem(CACHE_KEY); return data ? snapshotSchema.parse(JSON.parse(data)) : null; } catch { return null; }
}
export function useResults() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(restore);
  const [status, setStatus] = useState<SourceStatus | null>(null);
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [now, setNow] = useState(Date.now());
  const refresh = useRef<() => void>(() => {});
  useEffect(() => {
    let disposed = false, busy = false;
    let controller: AbortController | undefined;
    let timer: ReturnType<typeof setTimeout>;
    let current = restore();
    async function poll() {
      if (busy || disposed) return;
      clearTimeout(timer); busy = true; setLoading(true);
      controller = new AbortController();
      const deadline = setTimeout(() => controller?.abort(), 12_000);
      try {
        const response = await fetch(manifestUrl, { signal: controller.signal, cache: 'no-cache' });
        if (!response.ok) throw new Error('Manifest indisponível');
        const manifest = manifestSchema.parse(await response.json());
        let next = current;
        if (manifest.versao && manifest.arquivo && manifest.versao !== current?.versao) {
          const file = await fetch(snapshotUrl(manifestUrl, manifest.arquivo), { signal: controller.signal });
          if (!file.ok) throw new Error('Snapshot indisponível');
          next = snapshotSchema.parse(await file.json());
          if (next.versao !== manifest.versao || current && Date.parse(next.coletadoEm) < Date.parse(current.coletadoEm)) throw new Error('Versão incompatível');
        }
        if (disposed) return;
        current = next;
        setSnapshot(next); setStatus(manifest.status); setError(false); setNow(Date.now());
        if (next) { try { localStorage.setItem(CACHE_KEY, JSON.stringify(next)); } catch { /* Browser storage is optional. */ } }
      } catch {
        if (!disposed) { setError(true); setNow(Date.now()); }
      } finally {
        clearTimeout(deadline); busy = false;
        if (!disposed) { setLoading(false); timer = setTimeout(poll, 20_000 + Math.random() * 10_000); }
      }
    }
    refresh.current = () => { void poll(); };
    const clock = setInterval(() => setNow(Date.now()), 15_000);
    void poll();
    return () => { disposed = true; controller?.abort(); clearTimeout(timer); clearInterval(clock); };
  }, []);
  return { snapshot, status, error, loading, now, refresh: () => refresh.current() };
}
