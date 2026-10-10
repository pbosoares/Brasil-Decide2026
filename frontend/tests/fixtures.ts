import { states } from '../src/states';
import type { Result, Snapshot, Manifest } from '../src/data';

/** SIMULAÇÃO exclusiva de testes automatizados; nunca importada pelo frontend ou publicada. */
export function simulatedSnapshot(version = 'test-version'): Snapshot {
  const now = new Date().toISOString();
  const result = (uf: string, factor = 1): Result => ({ uf, geracao: version, atualizadoEm: now, divulgacaoPermitida: true, simulacao: false,
    candidatos: [{ id:'test-a', numero:'01', nome:'SIMULAÇÃO Candidatura A', partido:'TESTE', votos:60*factor, percentualOficial:60, destinacao:'Válido', situacao:'Não eleito', indicadoEleitoPeloTse:false }, { id:'test-b', numero:'02', nome:'SIMULAÇÃO Candidatura B', partido:'TESTE', votos:40*factor, percentualOficial:40, destinacao:'Válido', situacao:'Não eleito', indicadoEleitoPeloTse:false }],
    votos:{ total:100*factor, validos:100*factor, brancos:0, nulos:0, nulosTecnicos:0, anulados:0, anuladosSubJudice:0, semCandidato:0 },
    totalizacao:{ secoes:100*factor, secoesTotalizadas:50*factor, percentual:50, andamento:'p', totalizacaoFinal:false }, liderId:'test-a', diferencaVotos:20*factor,
  });
  return { versao:version, coletadoEm:now, eleicao:{ codigo:'6258', pleito:'3221', ciclo:'ele2026', nome:'SIMULAÇÃO exclusiva de teste', turno:{numero:2,data:'2026-10-25'}, simulacao:false }, nacional:{resultado:result('BR',27)}, estados:Object.fromEntries(states.map(([uf]) => [uf,{uf,resultado:result(uf)}])) };
}
export function simulatedManifest(version = 'test-version'): Manifest {
  const now = new Date().toISOString();
  return { schemaVersion:1, versao:version, arquivo:`snapshots/${version}.json`, status:{ estado:'DISPONIVEL', ultimaTentativa:now, ultimaVerificacao:now, ultimaAtualizacao:now, desatualizado:false, versao:version, fonte:'TSE', aviso:'SIMULAÇÃO exclusiva de testes automatizados' } };
}
