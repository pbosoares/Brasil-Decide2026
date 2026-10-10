package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SnapshotPublisherTest {
    // SIMULAÇÃO exclusiva de testes; nenhum objeto é enviado a um serviço externo.
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final ResultsService source = mock(ResultsService.class);
    final MemoryStorage storage = new MemoryStorage();
    final MutableClock clock = new MutableClock();
    final R2Properties properties = new R2Properties(true, "0".repeat(32), "test-bucket", "TEST_ACCESS", "TEST_SECRET", "results/", Duration.ofSeconds(10));
    SnapshotPublisher publisher() { return new SnapshotPublisher(source, storage, mapper, properties, clock); }
    Snapshot snapshot(String version, int seconds) {
        var at = clock.instant().plusSeconds(seconds);
        var candidates = List.of(new Candidato("test-a", "13", "SIMULAÇÃO A", "TESTE", 60, BigDecimal.valueOf(60), "Válido", "Não eleito", false),
                new Candidato("test-b", "22", "SIMULAÇÃO B", "TESTE", 40, BigDecimal.valueOf(40), "Válido", "Não eleito", false));
        var votes = new Votos(100, 100, 0, 0, 0, 0, 0, 0);
        var progress = new Totalizacao(100, 50, BigDecimal.valueOf(50), "p", false);
        Map<String, ResultadoEstadual> states = new TreeMap<>();
        for (String uf : ResultsService.UFS) {
            String upper = uf.toUpperCase(Locale.ROOT);
            states.put(upper, new ResultadoEstadual(upper, new Resultado(upper, version, at, true, false, candidates, votes, progress, "test-a", 20)));
        }
        var national = new Resultado("BR", version, at, true, false, candidates, votes, progress, "test-a", 20);
        return new Snapshot(version, at, new Eleicao("6258", "3221", "ele2026", "SIMULAÇÃO exclusiva de teste", new Turno(2, LocalDate.of(2026,10,25)), false), new ResultadoNacional(national), states);
    }
    void setSource(Snapshot snapshot, Instant verified) {
        when(source.publicationView()).thenReturn(Optional.of(new PublicationView(snapshot,
                new StatusDaFonte(snapshot == null ? "AGUARDANDO_FONTE" : "DISPONIVEL", verified, verified,
                        snapshot == null ? null : snapshot.nacional().resultado().atualizadoEm(), snapshot == null,
                        snapshot == null ? null : snapshot.versao(), "TSE", "SIMULAÇÃO exclusiva de teste"))));
    }
    PublicManifest manifest() throws Exception { return mapper.readValue(storage.objects.get("results/manifest.json").bytes(), PublicManifest.class); }

    @Test void uploadsAndConfirmsSnapshotBeforeSwitchingManifest() throws Exception {
        setSource(snapshot("v1",0), clock.instant()); publisher().publish();
        assertEquals(List.of("GET results/manifest.json", "PUT results/snapshots/v1.json", "GET results/snapshots/v1.json", "PUT results/manifest.json"), storage.events);
        assertEquals("v1", manifest().versao()); assertEquals("snapshots/v1.json", manifest().arquivo());
        assertEquals(SnapshotPublisher.SNAPSHOT_CACHE, storage.cache.get("results/snapshots/v1.json"));
        assertEquals(SnapshotPublisher.MANIFEST_CACHE, storage.cache.get("results/manifest.json"));
    }
    @Test void failedSnapshotUploadPreservesManifestThenRetries() throws Exception {
        var publisher = publisher(); setSource(snapshot("v1",0), clock.instant()); publisher.publish();
        byte[] before = storage.objects.get("results/manifest.json").bytes();
        setSource(snapshot("v2",1), clock.instant()); storage.failKey = "results/snapshots/v2.json"; publisher.publish();
        assertArrayEquals(before, storage.objects.get("results/manifest.json").bytes());
        storage.failKey = null; clock.advance(5); publisher.publish(); assertEquals("v2", manifest().versao());
    }
    @Test void failedManifestUploadDoesNotReuploadConfirmedSnapshot() throws Exception {
        setSource(snapshot("v1",0), clock.instant()); var publisher = publisher();
        storage.failKey = "results/manifest.json"; publisher.publish(); assertFalse(storage.objects.containsKey("results/manifest.json"));
        storage.failKey = null; clock.advance(5); publisher.publish();
        assertEquals("v1", manifest().versao());
        assertEquals(1, storage.events.stream().filter(e -> e.equals("PUT results/snapshots/v1.json")).count());
    }
    @Test void sourceStatusRefreshDoesNotRewriteVotesOrUseUploadTime() throws Exception {
        var snapshot = snapshot("v1",0); setSource(snapshot,clock.instant()); var publisher = publisher(); publisher.publish();
        clock.advance(30); setSource(snapshot, clock.instant()); publisher.publish();
        assertEquals(clock.instant(), manifest().status().ultimaVerificacao());
        assertEquals(snapshot.nacional().resultado().atualizadoEm(), manifest().status().ultimaAtualizacao());
        assertEquals(1, storage.events.stream().filter(e -> e.equals("PUT results/snapshots/v1.json")).count());
    }
    @Test void restartWithLostLocalDiskKeepsRemoteVotesAndMarksThemStale() throws Exception {
        setSource(snapshot("v1",0),clock.instant()); publisher().publish();
        byte[] before = storage.objects.get("results/snapshots/v1.json").bytes();
        setSource(null,clock.instant()); publisher().publish();
        assertEquals("v1",manifest().versao()); assertTrue(manifest().status().desatualizado());
        assertEquals("INDISPONIVEL",manifest().status().estado());
        assertArrayEquals(before,storage.objects.get("results/snapshots/v1.json").bytes());
    }
    @Test void concurrentPublisherCannotRollBackTheManifest() throws Exception {
        setSource(snapshot("v1",0),clock.instant()); var older = publisher(); older.publish();
        var v2 = snapshot("v2",1); setSource(v2,clock.instant()); publisher().publish();
        setSource(snapshot("v1",0),clock.instant()); older.publish(); // Conditional write conflicts.
        clock.advance(5); older.publish(); // Recovers v2 and rejects the older local snapshot.
        assertEquals("v2",manifest().versao());
    }
    @Test void corruptUploadConfirmationNeverCreatesAManifest() {
        setSource(snapshot("v1",0),clock.instant()); storage.corruptReadKey = "results/snapshots/v1.json"; publisher().publish();
        assertFalse(storage.objects.containsKey("results/manifest.json"));
    }
    @Test void sameVersionCannotReplaceAnExistingImmutableObject() {
        storage.objects.put("results/snapshots/v1.json",new ObjectStorage.Stored("{}".getBytes(),"\"existing\""));
        setSource(snapshot("v1",0),clock.instant()); publisher().publish();
        assertFalse(storage.objects.containsKey("results/manifest.json"));
        assertArrayEquals("{}".getBytes(),storage.objects.get("results/snapshots/v1.json").bytes());
    }
    @Test void waitingManifestHasNoFabricatedSnapshot() throws Exception {
        setSource(null,clock.instant()); publisher().publish();
        assertNull(manifest().versao()); assertNull(manifest().arquivo()); assertEquals(1,storage.objects.size());
    }
    @Test void invalidElectionAndAnOlderUfAreNeverPublished() throws Exception {
        var v1 = snapshot("v1",0); setSource(v1,clock.instant()); var publisher = publisher(); publisher.publish();
        var next = snapshot("v2",1); var states = new TreeMap<>(next.estados()); states.put("SP",v1.estados().get("SP"));
        var state = states.get("SP").resultado();
        states.put("SP",new ResultadoEstadual("SP",new Resultado("SP",state.geracao(),state.atualizadoEm().minusSeconds(1),true,false,state.candidatos(),state.votos(),state.totalizacao(),state.liderId(),state.diferencaVotos())));
        setSource(new Snapshot(next.versao(),next.coletadoEm(),next.eleicao(),next.nacional(),states),clock.instant()); publisher.publish();
        assertEquals("v1",manifest().versao());
        clock.advance(5);
        setSource(new Snapshot(next.versao(),next.coletadoEm(),new Eleicao("6257","3220","ele2026","SIMULAÇÃO",new Turno(1,LocalDate.of(2026,10,4)),false),next.nacional(),next.estados()),clock.instant());
        publisher.publish(); assertEquals("v1",manifest().versao());
    }
    static class MemoryStorage implements ObjectStorage {
        final Map<String,Stored> objects = new HashMap<>(); final Map<String,String> cache = new HashMap<>(); final List<String> events = new ArrayList<>();
        int sequence; String failKey, corruptReadKey;
        @Override public Optional<Stored> read(String key) {
            events.add("GET " + key);
            if (key.equals(corruptReadKey)) return Optional.of(new Stored("corrupt".getBytes(),"\"corrupt\""));
            return Optional.ofNullable(objects.get(key));
        }
        @Override public String put(String key, byte[] bytes, String cacheControl, String ifMatch, boolean createOnly) {
            events.add("PUT " + key);
            if (key.equals(failKey)) throw new IllegalStateException("SIMULAÇÃO de falha de envio");
            var old = objects.get(key);
            if (createOnly && old != null || ifMatch != null && (old == null || !ifMatch.equals(old.etag()))) throw new Conflict();
            String etag = "\"" + ++sequence + "\""; objects.put(key,new Stored(bytes.clone(),etag)); cache.put(key,cacheControl); return etag;
        }
    }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-25T20:00:00Z"); void advance(int seconds) { now = now.plusSeconds(seconds); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
