import type { Candidate } from './data';

export type CandidateProfile = Pick<Candidate, 'id' | 'numero' | 'nome' | 'partido'>;
// Identification and portraits from TSE; this registry contains no election results.
export const runoffCandidates: CandidateProfile[] = [
  { id: '280002542548', numero: '13', nome: 'Lula', partido: 'PT' },
  { id: '280002551544', numero: '22', nome: 'Flavio Bolsonaro', partido: 'PL' },
];
export function candidateColor(candidate?: Pick<Candidate, 'id'>) {
  if (candidate?.id === runoffCandidates[0].id) return '#e45359';
  if (candidate?.id === runoffCandidates[1].id) return '#4c8de8';
  return '#68c3d4';
}
export function candidatePhoto(candidate: CandidateProfile) {
  if (candidate.id === runoffCandidates[0].id) return '/candidates/lula.jpeg';
  if (candidate.id === runoffCandidates[1].id) return '/candidates/flavio.jpeg';
  return ('fotoUrl' in candidate ? candidate.fotoUrl : undefined) as string | undefined;
}
