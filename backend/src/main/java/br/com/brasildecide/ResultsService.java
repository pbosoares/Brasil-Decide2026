package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ResultsService {
    public static final List<String> UFS = List.of("ac", "al", "am", "ap", "ba", "ce", "df", "es", "go", "ma", "mg", "ms", "mt", "pa", "pb", "pe", "pi", "pr", "rj", "rn", "ro", "rr", "rs", "sc", "se", "sp", "to");
    private static final Logger log = LoggerFactory.getLogger(ResultsService.class);
    private final TseProperties properties;
    private final TseClient client;
    private final TseParser parser;
    private final SnapshotStore store;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();
    private volatile TseConfiguration configuration;
    private volatile Instant attempted, verified, blockedUntil = Instant.EPOCH;
    private volatile String state = "AGUARDANDO_FONTE";
    private int failures;
    private final java.util.concurrent.ExecutorService readers = java.util.concurrent.Executors.newFixedThreadPool(4);
    @jakarta.annotation.PreDestroy void stopReaders() { readers.shutdownNow(); }

    public ResultsService(TseProperties p, TseClient client, TseParser parser, SnapshotStore store) {
        this.properties = p; this.client = client; this.parser = parser; this.store = store;
        try {
            store.load().filter(s -> s.eleicao() != null && s.eleicao().turno().numero() == p.round()
                    && s.eleicao().turno().data().equals(p.electionDate()) && s.eleicao().simulacao() == p.simulation())
                    .ifPresent(s -> { SnapshotPublication.validate(s); current.set(s); state = "RECUPERADO_DO_DISCO"; });
        } catch (Exception e) { log.atWarn().addKeyValue("event", "snapshot_restore_failed").log("Snapshot local não pôde ser recuperado"); }
    }

    @Scheduled(fixedDelayString = "${tse.poll-delay-ms:30000}", initialDelayString = "${tse.initial-delay-ms:1000}")
    public synchronized void collect() {
        if (!properties.enabled() || Instant.now().isBefore(blockedUntil)) return;
        attempted = Instant.now();
        try {
            if (configuration == null) {
                URI configUri = URI.create(properties.baseUrl() + "/" + properties.environment() + "/comum/config/ele-c.json");
                var body = client.fetch(configUri, j -> TseConfiguration.discover(j, properties));
                configuration = TseConfiguration.discover(body, properties);
            }
            var election = configuration.election();
            // Four requests maximum at once, with a deadline for the entire batch.
            // A stalled UF never exposes an incomplete new snapshot.
            TseConfiguration source = configuration;
            Map<String, java.util.concurrent.Future<Resultado>> requests = new LinkedHashMap<>();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
            Resultado national = read("br", election, source);
            if (national.totalizacao().andamento().equals("n")) {
                verified = Instant.now(); state = "AGUARDANDO_FONTE"; failures = 0;
                return; // Official pre-election metadata is not a live vote snapshot.
            }
            Map<String, ResultadoEstadual> states = new TreeMap<>();
            try {
                for (String uf : UFS)
                    requests.put(uf, readers.submit(() -> read(uf, election, source)));
                for (String uf : UFS) states.put(uf.toUpperCase(Locale.ROOT), new ResultadoEstadual(uf.toUpperCase(Locale.ROOT),
                        requests.get(uf).get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS)));
            } catch (java.util.concurrent.ExecutionException ex) {
                if (ex.getCause() instanceof RuntimeException cause) throw cause;
                throw ex;
            } finally { requests.values().forEach(request -> request.cancel(true)); }
            Snapshot previous = current.get();
            Snapshot next = new Snapshot(UUID.randomUUID().toString(), Instant.now(), election,
                    new ResultadoNacional(national), Map.copyOf(states));
            SnapshotPublication.validate(next);
            if (previous != null) {
                if (national.atualizadoEm().isBefore(previous.nacional().resultado().atualizadoEm()))
                    throw new IllegalArgumentException("Arquivo nacional regrediu no tempo");
                for (String uf : states.keySet())
                    if (states.get(uf).resultado().atualizadoEm().isBefore(previous.estados().get(uf).resultado().atualizadoEm()))
                        throw new IllegalArgumentException("Arquivo estadual regrediu no tempo");
            }
            if (previous != null && sameGenerations(previous, next)) {
                verified = Instant.now(); state = "DISPONIVEL"; failures = 0; return;
            }
            store.save(next);
            current.set(next); verified = Instant.now(); state = "DISPONIVEL"; failures = 0;
            log.atInfo().addKeyValue("event", "snapshot_saved").addKeyValue("version", next.versao()).log("Snapshot oficial validado");
        } catch (Exception ex) {
            failures++;
            // Includes 404/429: no immediate retries or further requests in this cycle.
            blockedUntil = Instant.now().plusSeconds(Math.min(600, 30L << Math.min(failures, 5)));
            if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException http
                    && (http.getStatusCode().value() == 404 || http.getStatusCode().value() == 429))
                blockedUntil = Instant.now().plusSeconds(600);
            state = "INDISPONIVEL";
            configuration = null;
            log.atWarn().addKeyValue("event", "collection_failed").addKeyValue("type", ex.getClass().getSimpleName())
                    .addKeyValue("retry_after", blockedUntil).log("Coleta falhou; último snapshot preservado");
        }
    }

    private Resultado read(String uf, Eleicao election, TseConfiguration source) {
        var body = client.fetch(source.resultUri(uf), j -> parser.parse(j, election, uf));
        return parser.parse(body, election, uf);
    }
    private static boolean sameGenerations(Snapshot a, Snapshot b) {
        return a.nacional().resultado().geracao().equals(b.nacional().resultado().geracao())
                && UFS.stream().allMatch(uf -> a.estados().get(uf.toUpperCase(Locale.ROOT)).resultado().geracao()
                .equals(b.estados().get(uf.toUpperCase(Locale.ROOT)).resultado().geracao()));
    }
    static void validateSnapshot(Snapshot s) {
        if (s.nacional() == null || s.nacional().resultado() == null || s.estados() == null || s.estados().size() != 27)
            throw new IllegalArgumentException("Snapshot incompleto");
        Resultado national = s.nacional().resultado();
        if (!national.uf().equals("BR") || national.simulacao() != s.eleicao().simulacao())
            throw new IllegalArgumentException("Identidade nacional inválida");
        long stateVotes = 0;
        for (String uf : UFS) {
            var state = s.estados().get(uf.toUpperCase(Locale.ROOT));
            if (state == null || !state.uf().equalsIgnoreCase(uf) || !state.resultado().uf().equalsIgnoreCase(uf)) throw new IllegalArgumentException("UF ausente");
            if (state.resultado().simulacao() != s.eleicao().simulacao()) throw new IllegalArgumentException("Fases diferentes");
            stateVotes = Math.addExact(stateVotes, state.resultado().votos().total());
        }
        // BR includes ZZ. Only final files can be compared across different generation times.
        if (national.totalizacao().totalizacaoFinal()
                && s.estados().values().stream().allMatch(r -> r.resultado().totalizacao().andamento().equals("f"))
                && stateVotes > national.votos().total()) throw new IllegalArgumentException("UFs excedem total BR final");
    }
    public Optional<Snapshot> snapshot() { return Optional.ofNullable(current.get()); }
    /** Capture without locking the network collection thread. Skip a concurrent version swap. */
    public Optional<PublicationView> publicationView() {
        for (int i = 0; i < 3; i++) {
            Snapshot snapshot = current.get();
            var status = status();
            if (snapshot == current.get() && Objects.equals(snapshot == null ? null : snapshot.versao(), status.versao()))
                return Optional.of(new PublicationView(snapshot, status));
        }
        return Optional.empty();
    }
    public Optional<Eleicao> election() {
        Snapshot s = current.get();
        return s != null ? Optional.of(s.eleicao()) : Optional.ofNullable(configuration).map(TseConfiguration::election);
    }
    public StatusDaFonte status() {
        Snapshot s = current.get();
        Instant updated = s == null ? null : s.nacional().resultado().atualizadoEm();
        boolean stale = s == null || !state.equals("DISPONIVEL") || verified == null
                || verified.plus(properties.staleAfter()).isBefore(Instant.now());
        if (s != null && !s.nacional().resultado().totalizacao().totalizacaoFinal()) {
            stale |= updated.plus(properties.staleAfter()).isBefore(Instant.now());
            stale |= s.estados().values().stream().anyMatch(r -> !r.resultado().totalizacao().andamento().equals("f") && !r.resultado().totalizacao().andamento().equals("n")
                    && r.resultado().atualizadoEm().plus(properties.staleAfter()).isBefore(Instant.now()));
        }
        return new StatusDaFonte(state, attempted, verified, updated, stale, s == null ? null : s.versao(),
                "TSE", "Projeto independente, sem vínculo com a Justiça Eleitoral. Liderança parcial não significa eleição definida.");
    }
}
