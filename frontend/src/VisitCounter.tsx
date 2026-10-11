import { useEffect, useState } from 'react';

let registration: Promise<number> | undefined;
function register() {
  return registration ??= fetch('/api/v1/visits', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: crypto.randomUUID() }), signal: AbortSignal.timeout(8000),
  }).then(async response => {
    if (!response.ok) throw new Error('Contador indisponível');
    const data = await response.json();
    if (!Number.isSafeInteger(data.visitas) || data.visitas < 0) throw new Error('Contagem inválida');
    return data.visitas as number;
  });
}
export function VisitCounter() {
  const [total, setTotal] = useState<number>();
  useEffect(() => {
    let active = true;
    // Optional analytics must not interrupt the dashboard when a browser API is unavailable.
    Promise.resolve().then(register).then(value => { if (active) setTotal(value); }).catch(() => {});
    return () => { active = false; };
  }, []);
  return <p className="visit-counter" title="Aberturas desta página desde a instalação do contador. Recarregamentos contam; não representa pessoas únicas.">
    <strong>{total === undefined ? '—' : total.toLocaleString('pt-BR')}</strong> visitas ao site
    <small> desde a instalação do contador · inclui recarregamentos</small>
  </p>;
}
