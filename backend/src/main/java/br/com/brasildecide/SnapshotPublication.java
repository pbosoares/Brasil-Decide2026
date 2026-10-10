package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Validate the same public contract on publication and on recovery from the bucket. */
final class SnapshotPublication {
    static void validate(Snapshot s) {
        Objects.requireNonNull(s); Objects.requireNonNull(s.coletadoEm());
        if (s.versao() == null || !s.versao().matches("[a-zA-Z0-9_-]{1,80}")
                || s.eleicao() == null || !"ele2026".equals(s.eleicao().ciclo()) || s.eleicao().simulacao()
                || s.eleicao().codigo() == null || !s.eleicao().codigo().matches("\\d+")
                || s.eleicao().pleito() == null || s.eleicao().nome() == null
                || s.eleicao().turno() == null || s.eleicao().turno().numero() != 2
                || !LocalDate.of(2026, 10, 25).equals(s.eleicao().turno().data()))
            throw new IllegalArgumentException("Snapshot incompatível com o segundo turno oficial");
        ResultsService.validateSnapshot(s);
        var results = new ArrayList<Resultado>(); results.add(s.nacional().resultado());
        s.estados().values().forEach(state -> results.add(state.resultado()));
        for (var r : results) {
            Objects.requireNonNull(r.atualizadoEm()); Objects.requireNonNull(r.geracao());
            var v = Objects.requireNonNull(r.votos()); var t = Objects.requireNonNull(r.totalizacao());
            long[] counts = {v.total(), v.validos(), v.brancos(), v.nulos(), v.nulosTecnicos(), v.anulados(), v.anuladosSubJudice(), v.semCandidato(), t.secoes(), t.secoesTotalizadas(), r.diferencaVotos()};
            for (long count : counts) count(count);
            if (v.total() != TseParser.sum(v.validos(), v.brancos(), v.nulos(), v.anulados(), v.anuladosSubJudice(), v.semCandidato())
                    || v.nulosTecnicos() > v.nulos() || t.secoesTotalizadas() > t.secoes()
                    || !Set.of("n", "p", "f").contains(t.andamento())) throw new IllegalArgumentException("Totais inválidos");
            percentage(t.percentual());
            if (r.candidatos() == null || r.candidatos().isEmpty()) throw new IllegalArgumentException("Candidaturas ausentes");
            var ids = new HashSet<String>(); long votes = 0;
            for (var c : r.candidatos()) {
                if (c.id() == null || c.id().isBlank() || !ids.add(c.id()) || c.nome() == null || c.nome().isBlank()
                        || c.numero() == null || c.partido() == null || c.destinacao() == null || c.situacao() == null)
                    throw new IllegalArgumentException("Candidatura inválida");
                count(c.votos()); percentage(c.percentualOficial()); votes = Math.addExact(votes, c.votos());
            }
            var sorted = r.candidatos().stream().sorted(Comparator.comparingLong(Candidato::votos).reversed()).toList();
            long difference = sorted.getFirst().votos() - (sorted.size() > 1 ? sorted.get(1).votos() : 0);
            String leader = r.divulgacaoPermitida() && difference > 0 ? sorted.getFirst().id() : null;
            if (r.diferencaVotos() != difference || !Objects.equals(leader, r.liderId())
                    || r.divulgacaoPermitida() && votes != TseParser.sum(v.validos(), v.anulados(), v.anuladosSubJudice())
                    || !r.divulgacaoPermitida() && votes != 0) throw new IllegalArgumentException("Votação inválida");
        }
    }
    static boolean regresses(Snapshot next, Snapshot previous) {
        return next.coletadoEm().isBefore(previous.coletadoEm())
                || next.nacional().resultado().atualizadoEm().isBefore(previous.nacional().resultado().atualizadoEm())
                || ResultsService.UFS.stream().anyMatch(uf -> next.estados().get(uf.toUpperCase(Locale.ROOT)).resultado().atualizadoEm()
                        .isBefore(previous.estados().get(uf.toUpperCase(Locale.ROOT)).resultado().atualizadoEm()));
    }
    private static void count(long value) {
        if (value < 0 || value > 9_007_199_254_740_991L) throw new IllegalArgumentException("Contagem inválida");
    }
    private static void percentage(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Percentual inválido");
    }
}
