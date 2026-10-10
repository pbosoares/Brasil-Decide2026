package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TseParser {
    static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank())
            throw new IllegalArgumentException("Campo obrigatório ausente: " + field);
        return value.asText();
    }
    static JsonNode array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) throw new IllegalArgumentException("Lista ausente: " + field);
        return value;
    }
    static long count(JsonNode node, String field) {
        long value = Long.parseLong(required(node, field));
        if (value < 0) throw new IllegalArgumentException("Contagem negativa: " + field);
        return value;
    }
    static BigDecimal percentage(JsonNode node, String field) {
        BigDecimal value = new BigDecimal(required(node, field).replace(',', '.'));
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Percentual inválido: " + field);
        return value;
    }
    static boolean yes(JsonNode node, String field) {
        String value = required(node, field);
        if (!Set.of("s", "n").contains(value)) throw new IllegalArgumentException("Indicador inválido: " + field);
        return value.equals("s");
    }
    static long sum(long... values) { long total = 0; for (long v : values) total = Math.addExact(total, v); return total; }

    public Resultado parse(JsonNode root, Eleicao election, String uf) {
        if (!required(root, "ele").equals(election.codigo()) || !required(root, "t").equals("" + election.turno().numero())
                || !required(root, "f").equals(election.simulacao() ? "s" : "o")
                || !required(root, "cdabr").equalsIgnoreCase(uf)
                || !required(root, "tpabr").equals(uf.equals("br") ? "br" : "uf"))
            throw new IllegalArgumentException("Identidade EA20 incompatível");
        boolean disclose = yes(root, "dv");
        String progress = required(root, "and");
        if (!Set.of("n", "p", "f").contains(progress)) throw new IllegalArgumentException("Andamento inválido");
        boolean finalTotal = yes(root, "tf");
        JsonNode s = root.path("s"), v = root.path("v");
        long sections = count(s, "ts"), counted = count(s, "st");
        if (sum(counted, count(s, "snt")) != sections) throw new IllegalArgumentException("Seções inconsistentes");
        var totals = new Totalizacao(sections, counted, percentage(s, "pstn"), progress, finalTotal);
        var votes = new Votos(count(v, "tv"), count(v, "vv"), count(v, "vb"), count(v, "tvn"),
                count(v, "vnt"), count(v, "van"), count(v, "vansj"), count(v, "vscv"));
        if (votes.total() != sum(count(v, "vvc"), votes.brancos(), votes.nulos(), votes.semCandidato())
                || count(v, "vvc") != sum(votes.validos(), votes.anulados(), votes.anuladosSubJudice())
                || votes.nulos() != sum(count(v, "vn"), votes.nulosTecnicos()))
            throw new IllegalArgumentException("Totais de votos inconsistentes");
        List<Candidato> candidates = new ArrayList<>();
        int offices = 0;
        for (JsonNode office : array(root, "carg")) {
            if (count(office, "cd") != 1) throw new IllegalArgumentException("Cargo não presidencial");
            offices++;
            for (JsonNode group : array(office, "agr")) for (JsonNode party : array(group, "par"))
                for (JsonNode c : array(party, "cand")) candidates.add(new Candidato(required(c, "sqcand"),
                        required(c, "n"), required(c, "nmu"), required(party, "sg"), count(c, "vap"),
                        percentage(c, "pvapn"), required(c, "dvt"), required(c, "st"), yes(c, "e")));
        }
        if (offices != 1 || candidates.isEmpty() || candidates.stream().map(Candidato::id).distinct().count() != candidates.size())
            throw new IllegalArgumentException("Candidatos ausentes ou duplicados");
        long candidateVotes = 0;
        for (Candidato c : candidates) candidateVotes = Math.addExact(candidateVotes, c.votos());
        if (disclose && candidateVotes != count(v, "vvc")) throw new IllegalArgumentException("Votos de candidatos inconsistentes");
        if (!disclose && candidateVotes != 0) throw new IllegalArgumentException("Votação não autorizada");
        candidates.sort(Comparator.comparingLong(Candidato::votos).reversed().thenComparing(Candidato::id));
        long difference = candidates.size() > 1 ? candidates.getFirst().votos() - candidates.get(1).votos() : candidates.getFirst().votos();
        String leader = disclose && difference > 0 ? candidates.getFirst().id() : null;
        Instant updated = LocalDateTime.parse(required(root, "dg") + " " + required(root, "hg"),
                DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss")).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
        return new Resultado(uf.toUpperCase(Locale.ROOT), required(root, "idg"), updated, disclose,
                election.simulacao(), List.copyOf(candidates), votes, totals, leader, difference);
    }
}
