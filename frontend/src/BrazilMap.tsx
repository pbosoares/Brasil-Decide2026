import { useState } from 'react';
import map from './assets/brazil.json';
import { candidateColor, type CandidateProfile } from './candidates';
import { states, stateName } from './states';
import { number, percent, type Snapshot } from './data';

export default function BrazilMap({ snapshot, candidates, selected, select }: { snapshot: Snapshot | null; candidates: CandidateProfile[]; selected: string; select: (uf: string) => void }) {
  const [hovered, setHovered] = useState<string | null>(null);
  const active = hovered ? snapshot?.estados[hovered]?.resultado : null;
  const data = map as Record<string, { path: string; label: number[] }>;
  return <div className="map-wrap">
    <svg viewBox="0 0 600 570" aria-label="Mapa interativo das 27 unidades da federação" className="brazil-map">
      <defs><pattern id="map-grid" width="36" height="36" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r=".7" fill="#506072" opacity=".22"/></pattern></defs>
      <rect width="600" height="570" fill="url(#map-grid)"/>
      {states.map(([uf, name]) => {
        const result = snapshot?.estados[uf]?.resultado;
        const index = candidates.findIndex(c => c.id === result?.liderId);
        const fill = result?.divulgacaoPermitida && index >= 0 ? candidateColor(candidates[index]) : '#263341';
        const {path, label} = data[uf];
        return <g key={uf}>
          <path d={path} fill={fill} className={`state-path ${selected === uf ? 'selected' : ''}`} role="button" tabIndex={0} aria-label={`Selecionar ${name}`} aria-pressed={selected === uf}
            onClick={() => select(uf)} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); select(uf); } }} onMouseEnter={() => setHovered(uf)} onMouseLeave={() => setHovered(null)} onFocus={() => setHovered(uf)} onBlur={() => setHovered(null)}>
            <title>{name}: {result?.divulgacaoPermitida ? `${number(result.votos.total)} votos; ${percent(result.totalizacao.percentual)} das seções totalizadas` : 'Aguardando dados oficiais'}</title>
          </path>
          {uf !== 'DF' && <text x={label[0]} y={label[1]} className={`uf-label ${index >= 0 ? 'with-votes' : ''}`} textAnchor="middle" dominantBaseline="central">{uf}</text>}
          {uf === 'DF' && <><line x1={label[0]} y1={label[1]} x2={label[0]+29} y2={label[1]-17} stroke="#a8b5c6"/><text x={label[0]+32} y={label[1]-18} className="uf-label">DF</text></>}
        </g>;
      })}
    </svg>
    {hovered && <div className="map-tooltip" role="status"><strong>{stateName(hovered)} <span>{hovered}</span></strong>{active?.divulgacaoPermitida ? <><small>{percent(active.totalizacao.percentual)} das seções</small>{active.candidatos.map(c => <p key={c.id}>{c.nome}<b>{percent(c.percentualOficial)}</b><small>{number(c.votos)} votos</small></p>)}</> : <small>Aguardando dados oficiais</small>}</div>}
  </div>;
}
