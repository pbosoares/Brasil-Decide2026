import { z } from 'zod';
import { states } from './states';

const count = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const percentage = z.number().min(0).max(100);
const instant = z.string().datetime({ offset: true });
const candidateSchema = z.object({ id: z.string().min(1), numero: z.string(), nome: z.string().min(1), partido: z.string(), votos: count, percentualOficial: percentage, destinacao: z.string(), situacao: z.string(), indicadoEleitoPeloTse: z.boolean(), fotoUrl: z.url().refine(value => { const url = new URL(value); return url.protocol === 'https:' && url.hostname === 'resultados.tse.jus.br' && !url.username && !url.password; }, 'Foto deve ter origem oficial do TSE').optional() });
const resultSchema = z.object({ uf: z.string(), geracao: z.string(), atualizadoEm: instant, divulgacaoPermitida: z.boolean(), simulacao: z.literal(false), candidatos: z.array(candidateSchema), votos: z.object({ total: count, validos: count, brancos: count, nulos: count, nulosTecnicos: count, anulados: count, anuladosSubJudice: count, semCandidato: count }), totalizacao: z.object({ secoes: count, secoesTotalizadas: count, percentual: percentage, andamento: z.enum(['n', 'p', 'f']), totalizacaoFinal: z.boolean() }), liderId: z.string().nullable(), diferencaVotos: count });
export const statusSchema = z.object({ estado: z.enum(['AGUARDANDO_FONTE', 'RECUPERADO_DO_DISCO', 'DISPONIVEL', 'INDISPONIVEL']), ultimaTentativa: instant.nullable(), ultimaVerificacao: instant.nullable(), ultimaAtualizacao: instant.nullable(), desatualizado: z.boolean(), versao: z.string().nullable(), fonte: z.literal('TSE'), aviso: z.string() });
export const manifestSchema = z.object({ schemaVersion: z.literal(1), versao: z.string().min(1).nullable(), arquivo: z.string().nullable(), status: statusSchema }).superRefine((value, ctx) => {
  if ((value.versao === null) !== (value.arquivo === null) || value.versao !== value.status.versao) ctx.addIssue({ code: 'custom', message: 'Manifest inconsistente' });
});
export const snapshotSchema = z.object({ versao: z.string().min(1), coletadoEm: instant,
  eleicao: z.object({ codigo: z.string().regex(/^\d+$/), pleito: z.string(), ciclo: z.literal('ele2026'), nome: z.string(), turno: z.object({ numero: z.literal(2), data: z.literal('2026-10-25') }), simulacao: z.literal(false) }),
  nacional: z.object({ resultado: resultSchema }), estados: z.record(z.string(), z.object({ uf: z.string(), resultado: resultSchema })),
}).superRefine((value, ctx) => {
  const results = [value.nacional.resultado, ...Object.values(value.estados).map(s => s.resultado)];
  if (value.nacional.resultado.uf !== 'BR' || Object.keys(value.estados).length !== 27 || states.some(([uf]) => value.estados[uf]?.uf !== uf || value.estados[uf]?.resultado.uf !== uf)) ctx.addIssue({ code: 'custom', message: 'Abrangências incompletas' });
  for (const result of results) {
    const v = result.votos;
    const sorted = [...result.candidatos].sort((a,b) => b.votos - a.votos);
    const difference = sorted.length > 1 ? sorted[0].votos - sorted[1].votos : sorted[0]?.votos ?? 0;
    const leader = result.divulgacaoPermitida && difference > 0 ? sorted[0].id : null;
    if (result.divulgacaoPermitida && result.candidatos.reduce((total,c) => total+c.votos,0) !== v.validos+v.anulados+v.anuladosSubJudice) ctx.addIssue({ code: 'custom', message: 'Votação de candidatos inconsistente' });
    if (v.total !== v.validos + v.anulados + v.anuladosSubJudice + v.brancos + v.nulos + v.semCandidato || v.nulosTecnicos > v.nulos || result.totalizacao.secoesTotalizadas > result.totalizacao.secoes || result.candidatos.length === 0 || new Set(result.candidatos.map(c => c.id)).size !== result.candidatos.length || result.liderId !== leader || result.diferencaVotos !== difference || (!result.divulgacaoPermitida && result.candidatos.some(c => c.votos !== 0))) ctx.addIssue({ code: 'custom', message: 'Resultado inconsistente' });
  }
});
export type Candidate = z.infer<typeof candidateSchema>;
export type Result = z.infer<typeof resultSchema>;
export type Snapshot = z.infer<typeof snapshotSchema>;
export type SourceStatus = z.infer<typeof statusSchema>;
export type Manifest = z.infer<typeof manifestSchema>;
export const number = (value: number | undefined) => value === undefined ? '—' : new Intl.NumberFormat('pt-BR').format(value);
export const percent = (value: number | undefined) => value === undefined ? '—' : `${value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
export const time = (value?: string | null) => value ? new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(value)) : 'Ainda sem atualização';
export const candidateOrder = (candidates: Candidate[]) => [...candidates].sort((a,b) => a.numero.localeCompare(b.numero, 'pt-BR', { numeric: true }) || a.id.localeCompare(b.id));
export function isStale(snapshot: Snapshot | null, status: SourceStatus | null, now = Date.now()) {
  if (!snapshot || !status || status.desatualizado || status.estado !== 'DISPONIVEL' || !status.ultimaVerificacao || now - Date.parse(status.ultimaVerificacao) > 120_000) return true;
  return [snapshot.nacional.resultado, ...Object.values(snapshot.estados).map(s => s.resultado)].some(r => !r.totalizacao.totalizacaoFinal && r.totalizacao.andamento !== 'f' && r.totalizacao.andamento !== 'n' && now - Date.parse(r.atualizadoEm) > 120_000);
}
export function snapshotUrl(manifestUrl: string, path: string) {
  if (!/^snapshots\/[a-zA-Z0-9_-]+\.json$/.test(path)) throw new Error('Caminho de snapshot inválido');
  const base = new URL(manifestUrl, window.location.href);
  const url = new URL(path, base);
  if (url.origin !== base.origin) throw new Error('Origem de snapshot inválida');
  return url.href;
}
