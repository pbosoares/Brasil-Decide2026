package br.com.brasildecide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static br.com.brasildecide.Models.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultsServiceTest {
    @TempDir Path directory;
    Snapshot snapshot() {
        // SIMULAÇÃO estrutural de teste, sem votos. Nunca carregada pela aplicação.
        var election = new Eleicao("6258", "3221", "ele2026", "SIMULAÇÃO DE TESTE",
                new Turno(2, LocalDate.of(2026, 10, 25)), false);
        var total = new Totalizacao(0, 0, BigDecimal.ZERO, "n", false);
        var votes = new Votos(0, 0, 0, 0, 0, 0, 0, 0);
        var national = new Resultado("BR", "1", Instant.now(), false, false, List.of(), votes, total, null, 0);
        Map<String, ResultadoEstadual> states = new HashMap<>();
        for (String uf : ResultsService.UFS) states.put(uf.toUpperCase(), new ResultadoEstadual(uf.toUpperCase(),
                new Resultado(uf.toUpperCase(), "1", Instant.now(), false, false, List.of(), votes, total, null, 0)));
        return new Snapshot("test", Instant.now(), election, new ResultadoNacional(national), states);
    }
    @Test void atomicStoreRoundTripAndRestore() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var properties = ConfigurationTest.properties(directory);
        var store = new SnapshotStore(properties, mapper);
        assertTrue(store.load().isEmpty());
        Snapshot original = snapshot(); store.save(original);
        assertEquals(original, store.load().orElseThrow());
        try (var paths = java.nio.file.Files.list(directory)) { assertEquals(1, paths.count()); }
    }
    @Test void failedSourceKeepsPreviousSnapshotAndOpensCircuit() throws Exception {
        var client = mock(TseClient.class);
        var store = mock(SnapshotStore.class);
        Snapshot previous = snapshot();
        when(store.load()).thenReturn(Optional.of(previous));
        when(client.fetch(any(), any())).thenThrow(new IllegalArgumentException("Fonte indisponível"));
        var service = new ResultsService(ConfigurationTest.properties(directory), client, new TseParser(), store);
        service.collect(); service.collect();
        assertEquals(previous, service.snapshot().orElseThrow());
        assertEquals("INDISPONIVEL", service.status().estado());
        assertTrue(service.status().desatualizado());
        verify(client, times(1)).fetch(any(), any());
        verify(store, never()).save(any());
    }
    @Test void rejectsIncompleteSnapshot() {
        var previous = snapshot();
        assertThrows(IllegalArgumentException.class, () -> ResultsService.validateSnapshot(new Snapshot("test", Instant.now(),
                previous.eleicao(), previous.nacional(), Map.of())));
    }
    @Test void oneFailedUfNeverPublishesAPartialNewSnapshot() throws Exception {
        var client = mock(TseClient.class); var parser = mock(TseParser.class);
        var store = mock(SnapshotStore.class); Snapshot previous = snapshot();
        when(store.load()).thenReturn(Optional.of(previous));
        var mapper = new ObjectMapper();
        var config = mapper.readTree(getClass().getResourceAsStream("/fixtures/ele-c.json"));
        when(client.fetch(any(), any())).thenAnswer(call -> call.getArgument(0).toString().endsWith("ele-c.json") ? config : mapper.createObjectNode());
        when(parser.parse(any(), any(), anyString())).thenAnswer(call -> {
            String uf = call.getArgument(2);
            if (uf.equals("sp")) throw new IllegalArgumentException("UF incompleta");
            if (uf.equals("br")) {
                var n = previous.nacional().resultado();
                return new Resultado(n.uf(), n.geracao(), n.atualizadoEm(), n.divulgacaoPermitida(), n.simulacao(), n.candidatos(), n.votos(),
                        new Totalizacao(0, 0, BigDecimal.ZERO, "p", false), n.liderId(), n.diferencaVotos());
            }
            return previous.estados().get(uf.toUpperCase()).resultado();
        });
        var service = new ResultsService(ConfigurationTest.properties(directory), client, parser, store);
        try {
            service.collect();
            assertEquals(previous, service.snapshot().orElseThrow());
            assertEquals("INDISPONIVEL", service.status().estado());
            verify(store, never()).save(any());
        } finally { service.stopReaders(); }
    }
}
