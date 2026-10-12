import { lazy, Suspense, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ArrowDown, ArrowDownUp, ArrowRight, ArrowUpRight, Check, ChevronDown, ChevronRight, CircleHelp, Clock3, ExternalLink, Flag, Globe2, MapPin, RefreshCw, Search, ShieldCheck, Signal, Timer, UserRound, Vote, WifiOff } from 'lucide-react';
import { candidateOrder, isStale, number, percent, time, type Candidate, type Result } from './data';
import { candidateColor, candidatePhoto, runoffCandidates, type CandidateProfile } from './candidates';
import { useResults } from './useResults';
import BrazilMap from './BrazilMap';
import { VisitCounter } from './VisitCounter';
import { normalize, states, stateName } from './states';

const HistoryChart = lazy(() => import('./HistoryChart'));

function SectionTitle({ eyebrow, title, children }: { eyebrow: string; title: string; children?: ReactNode }) {
  return <div className="section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2></div>{children}</div>;
}
function CandidateCard({ candidate, result, visible }: { candidate: CandidateProfile; result?: Candidate; visible: boolean }) {
  const color = candidateColor(candidate);
  const photo = candidatePhoto(candidate);
  return <article className="candidate-card" style={{ '--candidate-color': color } as React.CSSProperties}>
    <div className="candidate-top"><span className="candidate-avatar"><UserRound size={26}/>{photo && <img src={photo} alt={`Foto de ${candidate.nome}`} loading="lazy" referrerPolicy="no-referrer" onError={event => { event.currentTarget.style.display = "none"; }}/>}</span><span className="candidate-number">{candidate.partido} · {candidate.numero}</span></div>
    <h3>{candidate.nome}</h3><p>{result?.situacao ?? 'Candidato à Presidência · 2º turno'}</p>
    <div className="candidate-score"><strong>{visible && result ? percent(result.percentualOficial) : '—'}<small>{visible ? 'dos votos válidos*' : 'percentual'}</small></strong><span>{visible && result ? number(result.votos) : '—'}<small>votos contabilizados</small></span></div>
    <div className="vote-bar"><span style={{ width: `${visible && result ? result.percentualOficial : 0}%`, background: color }}/></div>
  </article>;
}
function Metric({ icon, label, value, detail }: { icon: ReactNode; label: string; value: string; detail: string }) {
  return <article className="metric"><div className="metric-head">{icon}<span>{label}</span></div><strong>{value}</strong><small>{detail}</small></article>;
}
type SortKey = 'state' | 'votes' | 'progress' | 'leader' | 'candidate0' | 'candidate1';

export default function App() {
  const { snapshot, status, error, loading, now, refresh } = useResults();
  const [selected, setSelected] = useState('SP');
  const [query, setQuery] = useState('');
  useEffect(() => {
    const focusSearch = (event: KeyboardEvent) => {
      if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey && !(event.target instanceof HTMLInputElement) && !(event.target instanceof HTMLTextAreaElement) && !(event.target instanceof HTMLSelectElement)) {
        event.preventDefault(); document.querySelector<HTMLInputElement>('input[aria-label="Buscar estado ou UF"]')?.focus();
      }
    };
    window.addEventListener('keydown', focusSearch);
    return () => window.removeEventListener('keydown', focusSearch);
  }, []);
  const [sort, setSort] = useState<{key: SortKey; ascending: boolean}>({ key: 'state', ascending: true });
  const [history, setHistory] = useState<{version: string; states: Record<string, {at: string; progress: number}>}[]>([]);
  // History contains only snapshots actually observed in this session; no interpolated election data.
  useEffect(() => {
    if (snapshot) setHistory(previous => previous.at(-1)?.version === snapshot.versao ? previous : [...previous.slice(-29), { version: snapshot.versao, states: Object.fromEntries(Object.entries(snapshot.estados).map(([uf,s]) => [uf,{ at: s.resultado.atualizadoEm, progress: s.resultado.totalizacao.percentual }])) }]);
  }, [snapshot]);
  const national = snapshot?.nacional.resultado;
  const candidates: CandidateProfile[] = useMemo(() => national?.candidatos.length ? candidateOrder(national.candidatos).map(c => ({ ...c, nome: runoffCandidates.find(profile => profile.id === c.id)?.nome ?? c.nome })) : runoffCandidates, [national]);
  const visible = Boolean(national?.divulgacaoPermitida);
  const stateResult = snapshot?.estados[selected]?.resultado;
  const stale = Boolean(snapshot && (error || isStale(snapshot, status, now)));
  const officialFinal = Boolean(national?.totalizacao.totalizacaoFinal && visible);
  const waiting = !snapshot || !visible;
  const connected = (status?.estado === 'DISPONIVEL' || status?.estado === 'AGUARDANDO_FONTE' && Boolean(status.ultimaVerificacao)) && !error;
  const label = stale ? 'Dados desatualizados' : waiting ? 'Aguardando apuração' : officialFinal ? 'Totalização final' : 'Apuração em andamento';
  const historyPoints = history.map(h => h.states[selected]).filter(Boolean).filter((point,index,array) => index === 0 || array[index-1].at !== point.at).map(p => ({ horario: time(p.at), percentual: p.progress }));
  const leadership = (c: CandidateProfile) => snapshot ? Object.values(snapshot.estados).filter(s => s.resultado.divulgacaoPermitida && s.resultado.liderId === c.id).length : undefined;
  const tied = snapshot ? Object.values(snapshot.estados).filter(s => s.resultado.divulgacaoPermitida && s.resultado.liderId === null && s.resultado.candidatos.some(c => c.votos > 0)).length : undefined;
  const rows = useMemo(() => {
    const getValue = (uf: string, name: string) => {
      const r = snapshot?.estados[uf]?.resultado;
      if (sort.key === 'state') return name;
      if (sort.key === 'votes') return r?.divulgacaoPermitida ? r.votos.total : -1;
      if (sort.key === 'progress') return r?.totalizacao.percentual ?? -1;
      if (sort.key === 'leader') return r?.candidatos.find(c => c.id === r.liderId)?.nome ?? '';
      const c = candidates[sort.key === 'candidate0' ? 0 : 1];
      return r?.divulgacaoPermitida && r.totalizacao.andamento !== 'n' ? r.candidatos.find(a => a.id === c?.id)?.percentualOficial ?? -1 : -1;
    };
    return states.filter(([uf,name]) => normalize(`${uf} ${name}`).includes(normalize(query))).sort(([ua,na],[ub,nb]) => {
      const a = getValue(ua,na), b = getValue(ub,nb);
      return (typeof a === 'number' && typeof b === 'number' ? a-b : String(a).localeCompare(String(b),'pt-BR')) * (sort.ascending ? 1 : -1);
    });
  }, [query, sort, snapshot, candidates]);
  const changeSort = (key: SortKey) => setSort(s => ({ key, ascending: s.key === key ? !s.ascending : true }));
  const heading = (key: SortKey, title: string) => <th scope="col" aria-sort={sort.key === key ? sort.ascending ? 'ascending' : 'descending' : 'none'}><button onClick={() => changeSort(key)}>{title}{sort.key === key ? <ArrowDown size={13} className={sort.ascending ? 'reverse' : ''}/> : <ArrowDownUp size={13}/>}</button></th>;
  const stateCandidate = (r: Result | undefined, c: CandidateProfile | undefined) => r?.divulgacaoPermitida && r.totalizacao.andamento !== 'n' ? r.candidatos.find(a => a.id === c?.id) : undefined;

  return <>
    <a className="skip-link" href="#main">Pular para o conteúdo</a>
    <header className="topbar"><div className="topbar-inner"><a className="brand" href="#main" aria-label="Brasil Decide 2026"><span className="brand-icon"><span/><i/></span><span>brasil<span className="brand-light">decide</span><small>2026</small></span></a><nav aria-label="Navegação principal"><a href="#placar" className="active">Presidencial</a><a href="#estados">Por estado <ArrowUpRight size={13}/></a></nav><span className="independent"><ShieldCheck size={14}/> Projeto independente</span></div></header>
    <main id="main" className="page">
      <div className="intro election-hero"><img className="brazil-flag hero-watermark" src="/brazil-flag.svg" alt="Bandeira do Brasil" width="1000" height="700"/><div className="hero-copy"><div className="intro-meta"><span className="pill">ELEIÇÕES 2026</span><span className="meta-line"/><span>25 de outubro · 2º turno</span></div><h1>O Brasil,<br/>voto a voto<span>.</span></h1><p>Cada estado. Cada escolha. Acompanhe a apuração presidencial com dados oficiais do TSE.</p><div className="hero-actions"><a className="hero-primary" href="#estados">Explorar os estados <ArrowUpRight size={16}/></a><a className="hero-secondary" href="#sobre-dados">Como os dados chegam</a></div></div><div className="hero-national"><div className={`live-box ${stale ? 'stale' : ''}`}><span className="live-status"><i/>{label}</span><small><Clock3 size={12}/>{time(national?.atualizadoEm)}</small></div></div></div>
      <div className={`notice ${stale || error ? 'warning' : ''}`} role="status"><span className="notice-icon">{stale || error ? <WifiOff size={19}/> : <Timer size={19}/>}</span><div><strong>{stale ? 'Exibindo o último resultado válido' : error ? 'Não foi possível consultar a atualização' : waiting ? 'Tudo pronto para acompanhar a decisão' : officialFinal ? 'Totalização final informada pelo TSE' : 'A apuração ainda está em andamento'}</strong><p>{stale ? 'A atualização está atrasada. Os números anteriores foram preservados; consulte o horário da fonte.' : error ? 'Tentaremos novamente automaticamente. Nenhum resultado foi estimado.' : waiting ? 'Os números aparecerão quando os resultados oficiais do segundo turno forem disponibilizados e conectados.' : officialFinal ? 'A situação de cada candidatura é exibida conforme a fonte oficial.' : 'Liderança parcial não significa eleição definida. Os dados podem mudar.'}</p></div><button className="refresh-button" onClick={refresh} disabled={loading} aria-label="Atualizar resultados"><RefreshCw size={15} className={loading ? 'spinning' : ''}/><span>{loading ? 'Consultando' : 'Atualizar'}</span></button></div>
      <section aria-label="Estatísticas nacionais" className="metrics"><Metric icon={<Vote size={16}/>} label="Votos contabilizados" value={number(visible ? national?.votos.total : undefined)} detail="Total nacional, incluindo o exterior"/><Metric icon={<Check size={16}/>} label="Votos válidos" value={number(visible ? national?.votos.validos : undefined)} detail="Votos válidos para as candidaturas"/><Metric icon={<CircleHelp size={16}/>} label="Brancos e nulos" value={number(visible && national ? national.votos.brancos + national.votos.nulos : undefined)} detail={visible && national ? `${number(national.votos.brancos)} brancos · ${number(national.votos.nulos)} nulos` : 'Apresentados separadamente na apuração'}/><Metric icon={<Signal size={16}/>} label="Seções totalizadas" value={percent(national?.totalizacao.percentual)} detail={national ? `${number(national.totalizacao.secoesTotalizadas)} de ${number(national.totalizacao.secoes)} seções` : 'Aguardando o início da totalização'}/></section>
      <section id="estados" className="geography"><div className="panel map-panel"><SectionTitle eyebrow="27 UNIDADES DA FEDERAÇÃO" title="A escolha de cada estado"><span className="tag subtle"><MapPin size={13}/> Explore o mapa</span></SectionTitle><div className="map-topline"><span>Selecione uma UF para ver a apuração</span><small><span className="legend-dot neutral"/> Sem dados ou empate</small></div><BrazilMap snapshot={snapshot} candidates={candidates} selected={selected} select={setSelected}/><div className="map-legend">{candidates.map(c => <span key={c.id}><i style={{background:candidateColor(c)}}/>{c.nome}<b>{number(leadership(c))} UFs</b></span>)}{!candidates.length && <span><i className="neutral"/> As cores de liderança aparecerão com a apuração</span>}</div><div className="map-caption"><span>Malha simplificada · IBGE</span><span>{snapshot ? `${number(tied)} UFs empatadas` : 'Nenhuma liderança divulgada'}</span></div></div>
        <aside className="panel state-panel"><div className="state-heading"><span className="state-badge">{selected}</span><div><span className="eyebrow">APURAÇÃO ESTADUAL</span><h2>{stateName(selected)}</h2></div><label className="state-selector"><select aria-label="Selecionar estado" value={selected} onChange={e => setSelected(e.target.value)}>{states.map(([uf,name]) => <option key={uf} value={uf}>{name} ({uf})</option>)}</select><ChevronDown size={17}/></label></div><div className="state-total"><span>Votos contabilizados no estado</span><strong>{number(stateResult?.divulgacaoPermitida ? stateResult.votos.total : undefined)}</strong><small>{stateResult?.divulgacaoPermitida ? "Total informado pelo TSE" : "Aguardando dados oficiais"}</small></div><div className="state-progress"><div><span>Seções totalizadas</span><strong>{percent(stateResult?.totalizacao.percentual)}</strong></div><div className="progress-track"><i style={{width:`${stateResult?.totalizacao.percentual ?? 0}%`}}/></div><small>{stateResult ? `${number(stateResult.totalizacao.secoesTotalizadas)} de ${number(stateResult.totalizacao.secoes)} seções` : 'Aguardando dados oficiais'}</small></div>
          <div className="state-candidates">{candidates.length ? candidates.map(c => { const local=stateCandidate(stateResult,c); return <div key={c.id} className="local-candidate"><span className="local-avatar" style={{color:candidateColor(c)}}><UserRound size={18}/>{candidatePhoto(c) && <img src={candidatePhoto(c)} alt="" referrerPolicy="no-referrer" onError={event => { event.currentTarget.style.display = "none"; }}/>}</span><div><strong>{c.nome}</strong><small>{c.partido} · {c.numero}</small></div><span className="local-score"><b>{percent(local?.percentualOficial)}</b><small>{number(local?.votos)} votos</small></span></div>; }) : <div className="state-empty"><span><Vote size={25}/></span><h3>Aguardando apuração</h3><p>Os resultados de {stateName(selected)} serão exibidos aqui, com votos e percentuais oficiais.</p></div>}</div>
          <div className="state-difference"><span>Diferença de votos</span><strong>{number(stateResult?.divulgacaoPermitida ? stateResult.diferencaVotos : undefined)}</strong></div>
          <div className="history"><div className="history-title"><h3>Evolução da totalização</h3><Clock3 size={14}/></div>{historyPoints.length >= 2 ? <><Suspense fallback={<div className="history-empty">Preparando histórico…</div>}><HistoryChart points={historyPoints}/></Suspense><small>Atualizações recebidas nesta sessão; não inclui períodos anteriores.</small></> : <div className="history-empty"><span className="history-lines"/><small>O histórico aparecerá após duas atualizações oficiais nesta sessão.</small></div>}</div><div className="state-time"><Clock3 size={12}/>{time(stateResult?.atualizadoEm)}<span>Horário de Brasília</span></div>
        </aside>
      </section>
      <section id="placar" className="panel scoreboard"><SectionTitle eyebrow="A DECISÃO NACIONAL" title="Presidência da República"><span className="tag"><Flag size={13}/> 2º turno</span></SectionTitle>
        <div className="candidates">{candidates.map(c => <CandidateCard key={c.id} candidate={c} result={national?.candidatos.find(result => result.id === c.id)} visible={visible}/>)}</div>
        <div className="score-bottom"><span><span className="tiny-dot"/> {visible ? `${number(national?.diferencaVotos)} votos de diferença` : 'Nenhum voto divulgado nesta página'}</span><span><ShieldCheck size={13}/>{visible ? 'Percentuais informados pelo TSE*' : 'Candidaturas identificadas pela fonte oficial'}</span></div>
      </section>
      <section className="panel table-panel"><SectionTitle eyebrow="VISÃO COMPLETA" title="Apuração por estado"><label className="search"><Search size={16}/><input aria-label="Buscar estado ou UF" placeholder="Buscar estado ou UF" value={query} onChange={e => setQuery(e.target.value)}/><kbd>/</kbd></label></SectionTitle><div className="table-scroll"><table><caption className="sr-only">Resultados das 27 UFs. Clique no cabeçalho para ordenar e no estado para selecionar.</caption><thead><tr>{heading('state','Estado')}{heading('votes','Votos contabilizados')}{heading('candidate0',candidates[0]?.nome ?? 'Candidatura 1')}{heading('candidate1',candidates[1]?.nome ?? 'Candidatura 2')}{heading('progress','Seções totalizadas')}{heading('leader','Liderança')}<th scope="col"><span className="sr-only">Selecionar</span></th></tr></thead><tbody>{rows.map(([uf,name]) => { const r=snapshot?.estados[uf]?.resultado; const leader = r?.candidatos.find(c => c.id === r.liderId); return <tr key={uf} className={selected === uf ? 'selected-row' : ''}><td><button className="state-row-button" onClick={() => { setSelected(uf); document.getElementById('estados')?.scrollIntoView({behavior:'smooth',block:'start'}); }}><span className="uf-chip">{uf}</span><span>{name}</span></button></td><td>{number(r?.divulgacaoPermitida ? r.votos.total : undefined)}</td>{candidates.slice(0,2).length === 2 ? candidates.slice(0,2).map(c => <td key={c.id}><span className="candidate-percentage" style={{color:candidateColor(c)}}>{percent(stateCandidate(r,c)?.percentualOficial)}</span></td>) : <><td>—</td><td>—</td></>}<td><span className="table-progress">{percent(r?.totalizacao.percentual)}<i><b style={{width:`${r?.totalizacao.percentual ?? 0}%`}}/></i></span></td><td>{leader && r?.divulgacaoPermitida ? <span className="leader-pill" style={{color:candidateColor(leader)}}>{leader.nome}</span> : <span className="waiting-text">{r?.divulgacaoPermitida && r.candidatos.some(c => c.votos>0) ? 'Empate' : 'Aguardando'}</span>}</td><td><button className="row-chevron" aria-label={`Ver ${name}`} onClick={() => {setSelected(uf);document.getElementById('estados')?.scrollIntoView({behavior:'smooth'});}}><ChevronRight size={15}/></button></td></tr>; })}</tbody></table>{rows.length === 0 && <div className="no-search">Nenhum estado encontrado. Tente outro nome ou sigla.</div>}</div><div className="table-footer"><span>{rows.length} de 27 unidades da federação</span><span><Globe2 size={12}/> Total nacional inclui votos do exterior</span></div></section>
      <div id="sobre-dados" className="source-strip"><div><span className={`connection-dot ${connected ? 'connected' : ''}`}/><strong>{error ? 'Conexão com os dados indisponível' : stale ? 'Atualização da fonte atrasada' : connected ? 'Fonte oficial verificada' : 'Fonte ainda não conectada'}</strong><span>Consulta automática a cada 5–6 segundos</span></div><a href="https://resultados.tse.jus.br/" target="_blank" rel="noreferrer">Consultar o TSE <ExternalLink size={13}/></a></div>
      <footer><div className="footer-main"><a className="footer-brand" href="#main">brasil<span>decide</span> <small>2026</small></a><p>Informação para acompanhar. Transparência para confiar.</p><span>Segundo turno presidencial · 25/10/2026</span></div><div className="footer-legal"><ShieldCheck size={16}/><p>Projeto independente, sem vínculo oficial com a Justiça Eleitoral. Resultados eleitorais provenientes do TSE. {visible && '*Percentuais e destinação dos votos conforme a fonte oficial.'} Liderança parcial não significa eleição definida.</p><a href="https://www.tse.jus.br/eleicoes/informacoes-tecnicas-sobre-a-divulgacao-de-resultados" target="_blank" rel="noreferrer">Sobre os dados <ArrowRight size={13}/></a></div></footer>
      <VisitCounter/>
    </main>
  </>;
}
