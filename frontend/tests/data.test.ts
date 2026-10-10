import { describe, it, expect } from 'vitest';
import { snapshotSchema, manifestSchema, isStale } from '../src/data';
import { simulatedSnapshot, simulatedManifest } from './fixtures';

describe('contrato do segundo turno presidencial', () => {
  it('aceita um snapshot completo e um manifest consistente', () => {
    expect(snapshotSchema.safeParse(simulatedSnapshot()).success).toBe(true);
    expect(manifestSchema.safeParse(simulatedManifest()).success).toBe(true);
  });
  it('rejeita turno, data e fase incompatíveis', () => {
    const snapshot = simulatedSnapshot();
    expect(snapshotSchema.safeParse({...snapshot, eleicao:{...snapshot.eleicao,turno:{numero:1,data:'2026-10-04'}}}).success).toBe(false);
    expect(snapshotSchema.safeParse({...snapshot, eleicao:{...snapshot.eleicao,simulacao:true}}).success).toBe(false);
  });
  it('rejeita UF ausente, contagens inválidas e liderança inventada', () => {
    const snapshot = simulatedSnapshot(); delete snapshot.estados.SP;
    expect(snapshotSchema.safeParse(snapshot).success).toBe(false);
    const other = simulatedSnapshot(); other.nacional.resultado.votos.total = Number.MAX_SAFE_INTEGER + 1;
    expect(snapshotSchema.safeParse(other).success).toBe(false);
    const leadership = simulatedSnapshot(); leadership.nacional.resultado.liderId = 'inventado';
    expect(snapshotSchema.safeParse(leadership).success).toBe(false);
  });
  it('detecta divergência entre manifest e status', () => {
    const manifest = simulatedManifest(); manifest.status.versao = 'outra-versao';
    expect(manifestSchema.safeParse(manifest).success).toBe(false);
  });
  it('rejeita fotos sem origem oficial', () => {
    const snapshot = simulatedSnapshot(); snapshot.nacional.resultado.candidatos[0].fotoUrl='https://example.com/foto.jpg';
    expect(snapshotSchema.safeParse(snapshot).success).toBe(false);
  });
  it('sinaliza idade da fonte mesmo quando a rede responde', () => {
    const snapshot = simulatedSnapshot(), status = simulatedManifest().status;
    expect(isStale(snapshot,status)).toBe(false);
    expect(isStale(snapshot,status,Date.now()+180000)).toBe(true);
    expect(isStale(null,status)).toBe(true);
  });
});
