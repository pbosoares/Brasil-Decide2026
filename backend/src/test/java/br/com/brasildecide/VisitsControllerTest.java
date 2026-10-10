package br.com.brasildecide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

class VisitsControllerTest {
    @TempDir Path directory;
    private static final String ORIGIN = "https://brasildecide.pablo-soares.tech";
    @Test void persistsAndDoesNotCountDuplicateRegistration() throws Exception {
        var counter = new VisitsController(directory.toString(), ORIGIN);
        var visit = new VisitsController.Visit(UUID.randomUUID().toString());
        assertEquals(1, counter.register(ORIGIN, visit).getBody().visitas());
        assertEquals(1, counter.register(ORIGIN, visit).getBody().visitas());
        assertEquals(1, new VisitsController(directory.toString(), ORIGIN).count().getBody().visitas());
    }
    @Test void rejectsForeignOriginAndInvalidId() throws Exception {
        var counter = new VisitsController(directory.toString(), ORIGIN);
        assertThrows(ResponseStatusException.class, () -> counter.register("https://other.example", new VisitsController.Visit(UUID.randomUUID().toString())));
        assertThrows(ResponseStatusException.class, () -> counter.register(ORIGIN, new VisitsController.Visit("bad")));
        assertEquals(0, counter.count().getBody().visitas());
    }
}
