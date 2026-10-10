package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "publication.r2.enabled", havingValue = "true")
public class SnapshotPublisher {
    static final String SNAPSHOT_CACHE = "public,max-age=31536000,immutable";
    static final String MANIFEST_CACHE = "public,max-age=5,s-maxage=5,must-revalidate";
    private static final Logger log = LoggerFactory.getLogger(SnapshotPublisher.class);
    private final ResultsService source;
    private final ObjectStorage storage;
    private final ObjectMapper mapper;
    private final R2Properties properties;
    private final Clock clock;
    private boolean initialized;
    private String manifestEtag, confirmedVersion;
    private byte[] confirmedBytes;
    private Snapshot published;
    private Instant retryAfter = Instant.EPOCH;
    private int failures;

    @org.springframework.beans.factory.annotation.Autowired
    public SnapshotPublisher(ResultsService source, ObjectStorage storage, ObjectMapper mapper, R2Properties properties) {
        this(source, storage, mapper, properties, Clock.systemUTC());
    }
    SnapshotPublisher(ResultsService source, ObjectStorage storage, ObjectMapper mapper, R2Properties properties, Clock clock) {
        this.source = source; this.storage = storage; this.mapper = mapper; this.properties = properties; this.clock = clock;
    }

    @Scheduled(scheduler = "publicationScheduler", fixedDelayString = "${publication.poll-delay-ms:5000}", initialDelayString = "${publication.initial-delay-ms:2000}")
    public synchronized void publish() {
        if (!properties.enabled() || clock.instant().isBefore(retryAfter)) return;
        try {
            if (!initialized) recover();
            var view = source.publicationView();
            if (view.isEmpty()) return;
            Snapshot next = view.get().snapshot();
            if (next != null) {
                SnapshotPublication.validate(next);
                if (published != null && SnapshotPublication.regresses(next, published))
                    throw new IllegalArgumentException("Publicação regrediu em relação ao bucket");
                ensureSnapshot(next);
            } else next = published; // A collector with a lost disk never erases the last public version.
            var status = statusFor(view.get(), next);
            var manifest = new PublicManifest(1, next == null ? null : next.versao(),
                    next == null ? null : "snapshots/" + next.versao() + ".json", status);
            String etag = storage.put(key("manifest.json"), mapper.writeValueAsBytes(manifest), MANIFEST_CACHE,
                    manifestEtag, manifestEtag == null);
            // Advance only after the conditional manifest PUT succeeds.
            published = next; manifestEtag = etag; failures = 0; retryAfter = Instant.EPOCH;
        } catch (Exception ex) {
            failures++;
            retryAfter = clock.instant().plusSeconds(Math.min(60, 5L << Math.min(failures - 1, 4)));
            if (ex instanceof ObjectStorage.Conflict) initialized = false;
            log.atWarn().addKeyValue("event", "publication_failed").addKeyValue("type", ex.getClass().getSimpleName())
                    .addKeyValue("retry_after", retryAfter).log("Publicação falhou; manifest anterior preservado");
        }
    }

    private void recover() throws Exception {
        var existing = storage.read(key("manifest.json"));
        Snapshot previous = null;
        if (existing.isPresent()) {
            var manifest = mapper.readValue(existing.get().bytes(), PublicManifest.class);
            if (manifest.schemaVersion() != 1 || manifest.status() == null || !"TSE".equals(manifest.status().fonte())
                    || !Objects.equals(manifest.versao(), manifest.status().versao())
                    || (manifest.versao() == null) != (manifest.arquivo() == null))
                throw new IllegalArgumentException("Manifest remoto inválido");
            if (manifest.versao() != null) {
                if (!manifest.versao().matches("[a-zA-Z0-9_-]{1,80}") || !manifest.arquivo().equals("snapshots/" + manifest.versao() + ".json"))
                    throw new IllegalArgumentException("Caminho remoto inválido");
                var object = storage.read(key(manifest.arquivo())).orElseThrow(() -> new IllegalArgumentException("Snapshot remoto ausente"));
                previous = mapper.readValue(object.bytes(), Snapshot.class); SnapshotPublication.validate(previous);
                if (!previous.versao().equals(manifest.versao())) throw new IllegalArgumentException("Versão remota incompatível");
                confirmedVersion = previous.versao(); confirmedBytes = object.bytes();
            }
        }
        published = previous; manifestEtag = existing.map(ObjectStorage.Stored::etag).orElse(null); initialized = true;
    }
    private void ensureSnapshot(Snapshot snapshot) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(snapshot);
        if (snapshot.versao().equals(confirmedVersion)) {
            if (!Arrays.equals(bytes, confirmedBytes)) throw new IllegalArgumentException("Versão imutável com conteúdo diferente");
            return;
        }
        String key = key("snapshots/" + snapshot.versao() + ".json");
        try { storage.put(key, bytes, SNAPSHOT_CACHE, null, true); }
        catch (ObjectStorage.Conflict conflict) { /* Retry/restart: compare the immutable object before reuse. */ }
        var uploaded = storage.read(key).orElseThrow(() -> new IllegalArgumentException("Upload não confirmado"));
        if (!Arrays.equals(bytes, uploaded.bytes())) throw new IllegalArgumentException("Upload com conteúdo diferente");
        confirmedVersion = snapshot.versao(); confirmedBytes = bytes;
    }
    private StatusDaFonte statusFor(PublicationView view, Snapshot next) {
        var status = view.status();
        if (view.snapshot() != null && Objects.equals(view.snapshot().versao(), status.versao())) return status;
        if (view.snapshot() == null && next == null && status.versao() == null) return status;
        if (view.snapshot() != null) throw new IllegalArgumentException("Status incompatível com o snapshot");
        return new StatusDaFonte("INDISPONIVEL", status.ultimaTentativa(), status.ultimaVerificacao(),
                next.nacional().resultado().atualizadoEm(), true, next.versao(), "TSE", status.aviso());
    }
    private String key(String suffix) { return properties.prefix() + suffix; }
}
