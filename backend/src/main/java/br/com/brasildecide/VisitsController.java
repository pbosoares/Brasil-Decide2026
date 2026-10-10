package br.com.brasildecide;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VisitsController {
    private final Path file;
    private final String origin;
    private long total;
    private final LinkedHashMap<String, Boolean> recent = new LinkedHashMap<>();
    public VisitsController(@Value("${tse.snapshot-directory:data}") String directory,
                            @Value("${visits.origin:https://brasildecide.pablo-soares.tech}") String origin) throws IOException {
        file = Path.of(directory).resolve("visits.txt");
        this.origin = origin;
        if (Files.exists(file)) {
            total = Long.parseLong(Files.readString(file).trim());
            if (total < 0) throw new IOException("Invalid visits total");
        }
    }
    public record Count(long visitas) {}
    public record Visit(String id) {}
    @GetMapping("/api/v1/visits")
    public synchronized ResponseEntity<Count> count() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Count(total));
    }
    @PostMapping("/api/v1/visits")
    public synchronized ResponseEntity<Count> register(@RequestHeader(value="Origin", required=false) String requestOrigin,
                                                       @RequestBody Visit visit) {
        if (!origin.equals(requestOrigin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (visit.id() == null || !visit.id().matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!recent.containsKey(visit.id())) {
            try {
                long next = Math.addExact(total, 1);
                Files.createDirectories(file.getParent());
                Path temporary = Files.createTempFile(file.getParent(), "visits-", ".tmp");
                try {
                    Files.writeString(temporary, Long.toString(next));
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporary); }
                total = next;
                recent.put(visit.id(), true);
                if (recent.size() > 10_000) recent.remove(recent.keySet().iterator().next());
            } catch (IOException | ArithmeticException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        return count();
    }
}
