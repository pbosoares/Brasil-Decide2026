package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TseParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TseParser parser = new TseParser();
    private final Eleicao historical = new Eleicao("6257", "3220", "ele2026", "Fixture oficial histórica",
            new Turno(1, LocalDate.of(2026, 10, 4)), false);
    JsonNode fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/fixtures/" + name)) { return mapper.readTree(stream); }
    }
    @Test void officialPreElectionMetadataAllowsPendingSituationButNoInventedResult() throws Exception {
        var election = new Eleicao("6258", "3221", "ele2026", "Segundo turno",
                new Turno(2, LocalDate.of(2026, 10, 25)), false);
        var root = (ObjectNode) fixture("br-2026-t2-waiting.json");
        var result = parser.parse(root, election, "br");
        assertEquals("n", result.totalizacao().andamento());
        assertEquals(0, result.votos().total()); assertNull(result.liderId());
        assertEquals(2, result.candidatos().size());
        root.put("and", "p");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root, election, "br"));
    }
    @Test void parsesOfficialNationalHistoricalFile() throws Exception {
        var result = parser.parse(fixture("br-2026-t1.json"), historical, "br");
        assertEquals(125275835L, result.votos().total());
        assertEquals(119300788L, result.votos().validos());
        assertEquals(2300798L, result.votos().brancos());
        assertEquals(3674249L, result.votos().nulos());
        assertEquals(0, result.totalizacao().percentual().compareTo(BigDecimal.valueOf(100)));
        assertTrue(result.totalizacao().totalizacaoFinal());
        assertTrue(result.diferencaVotos() > 0);
        assertNotNull(result.liderId());
    }
    @Test void parsesOfficialStateSeparately() throws Exception {
        var result = parser.parse(fixture("sp-2026-t1.json"), historical, "sp");
        assertEquals("SP", result.uf());
        assertTrue(result.votos().total() < 125275835L);
    }
    @Test void rejectsWrongRoundAndSimulation() throws Exception {
        var root = (ObjectNode) fixture("br-2026-t1.json");
        root.put("t", "2");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root, historical, "br"));
        root.put("t", "1").put("f", "s");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root, historical, "br"));
    }
    @Test void rejectsMissingAndInconsistentTotals() throws Exception {
        var root = (ObjectNode) fixture("br-2026-t1.json");
        ((ObjectNode) root.get("v")).remove("vv");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root, historical, "br"));
        var other = (ObjectNode) fixture("br-2026-t1.json");
        ((ObjectNode) other.get("s")).put("st", "0");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(other, historical, "br"));
    }
    @Test void blocksNonDisclosableVotes() throws Exception {
        var root = (ObjectNode) fixture("br-2026-t1.json");
        root.put("dv", "n");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root, historical, "br"));
    }
    @Test void rejectsNegativeAndOverflowingCounts() throws Exception {
        var root = (ObjectNode) fixture("br-2026-t1.json");
        ((ObjectNode) root.get("v")).put("tv", "-1");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(root, historical, "br"));
    }
    @Test void tieAndZeroVotesHaveNoLeader() throws Exception {
        // SIMULAÇÃO derivada em memória de fixture oficial; nunca gravada como produção.
        var root = (ObjectNode) fixture("br-2026-t1.json");
        int candidates = 0;
        for (var office : root.get("carg")) for (var group : office.get("agr"))
            for (var party : group.get("par")) for (var candidate : party.get("cand")) {
                ((ObjectNode) candidate).put("vap", "10"); candidates++;
            }
        ObjectNode v = (ObjectNode) root.get("v");
        for (String field : java.util.List.of("tv", "vvc", "vv", "vnom")) v.put(field, "" + candidates * 10L);
        for (String field : java.util.List.of("vb", "tvn", "vn", "vnt", "van", "vansj", "vscv")) v.put(field, "0");
        var tied = parser.parse(root, historical, "br");
        assertNull(tied.liderId()); assertEquals(0, tied.diferencaVotos());
        for (var office : root.get("carg")) for (var group : office.get("agr"))
            for (var party : group.get("par")) for (var candidate : party.get("cand")) ((ObjectNode) candidate).put("vap", "0");
        for (String field : java.util.List.of("tv", "vvc", "vv", "vnom")) v.put(field, "0");
        root.put("dv", "n");
        assertNull(parser.parse(root, historical, "br").liderId());
    }
}
