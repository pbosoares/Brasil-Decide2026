package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import java.util.Locale;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ResultsController {
    private final ResultsService service;
    public ResultsController(ResultsService service) { this.service = service; }
    private <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    private Snapshot snapshot() {
        return service.snapshot().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
    }
    @GetMapping("/elections/current") public ResponseEntity<Eleicao> election() {
        return response(service.election().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)));
    }
    @GetMapping("/results/national") public ResponseEntity<ResultadoNacional> national() { return response(snapshot().nacional()); }
    @GetMapping("/results/states") public ResponseEntity<?> states() { return response(snapshot().estados().values().stream().sorted(java.util.Comparator.comparing(ResultadoEstadual::uf)).toList()); }
    @GetMapping("/results/states/{uf}") public ResponseEntity<ResultadoEstadual> state(@PathVariable String uf) {
        String normalized = uf.toLowerCase(Locale.ROOT);
        if (!ResultsService.UFS.contains(normalized)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        return response(snapshot().estados().get(normalized.toUpperCase(Locale.ROOT)));
    }
    @GetMapping("/results/status") public ResponseEntity<StatusDaFonte> status() { return response(service.status()); }
    @GetMapping("/results/manifest.json") public ResponseEntity<PublicManifest> manifest() {
        var status = service.status();
        return response(new PublicManifest(1, status.versao(), status.versao() == null ? null : "snapshots/" + status.versao() + ".json", status));
    }
    // Local bridge: if collection advances between the two requests, retry the manifest.
    // Production CDN publication still uploads an immutable snapshot before the manifest.
    @GetMapping("/results/snapshots/{version}.json") public ResponseEntity<Snapshot> version(@PathVariable String version) {
        var snapshot = snapshot();
        if (!snapshot.versao().equals(version)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT);
        return response(snapshot);
    }
}
