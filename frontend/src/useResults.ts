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
    let disposed = false, busy = false, pending = false, failures = 0;
    let controller: AbortController | undefined;
    let timer: ReturnType<typeof setTimeout>;
    let current = restore();
    async function poll() {
      if (disposed) return;
      if (busy) { pending = true; return; }
      if (!navigator.onLine) { setError(true); setNow(Date.now()); return; }
      clearTimeout(timer); busy = true; setLoading(true);
      controller = new AbortController();
      const deadline = setTimeout(() => controller?.abort(), 12_000);
      try {
        const response = await fetch(manifestUrl, { signal: controller.signal, cache: 'default' });
        if (!response.ok) throw new Error('Manifest indisponível');
        const manifest = manifestSchema.parse(await response.json());
        let next = current;
        if (manifest.versao && manifest.arquivo && manifest.versao !== current?.versao) {
          const file = await fetch(snapshotUrl(manifestUrl, manifest.arquivo), { signal: controller.signal });
          if (!file.ok) throw new Error('Snapshot indisponível');
          next = snapshotSchema.parse(await file.json());
          if (next.versao !== manifest.versao || current && (Date.parse(next.coletadoEm) < Date.parse(current.coletadoEm) || [next.nacional.resultado, ...Object.values(next.estados).map(state => state.resultado)].some(result => Date.parse(result.atualizadoEm) < Date.parse((result.uf === 'BR' ? current!.nacional.resultado : current!.estados[result.uf].resultado).atualizadoEm)))) throw new Error('Versão incompatível ou abrangência regrediu no tempo');
        }
        if (disposed) return;
        const changed = next !== current;
        current = next; failures = 0;
        setSnapshot(next); setStatus(manifest.status); setError(false); setNow(Date.now());
        if (next && changed) { try { localStorage.setItem(CACHE_KEY, JSON.stringify(next)); } catch { /* Browser storage is optional. */ } }
      } catch {
        if (!disposed) { failures++; setError(true); setNow(Date.now()); }
      } finally {
        clearTimeout(deadline); busy = false;
        if (!disposed) {
          setLoading(false);
          const delay = pending ? 0 : failures ? Math.min(30_000, 2_000 * 2 ** Math.min(failures - 1, 4)) : document.hidden ? 30_000 : 5_000;
          pending = false; timer = setTimeout(poll, delay + (delay ? Math.random() * 1_000 : 0));
        }
      }
    }
    refresh.current = () => { void poll(); };
    const resume = () => { if (!document.hidden && navigator.onLine) { failures = 0; void poll(); } };
    const offline = () => { clearTimeout(timer); controller?.abort(); setError(true); setNow(Date.now()); };
    document.addEventListener('visibilitychange', resume);
    window.addEventListener('online', resume); window.addEventListener('offline', offline);
    const clock = setInterval(() => setNow(Date.now()), 5_000);
    void poll();
    return () => { document.removeEventListener('visibilitychange', resume); window.removeEventListener('online', resume); window.removeEventListener('offline', offline); disposed = true; controller?.abort(); clearTimeout(timer); clearInterval(clock); };
  }, []);
  return { snapshot, status, error, loading, now, refresh: () => refresh.current() };
}
