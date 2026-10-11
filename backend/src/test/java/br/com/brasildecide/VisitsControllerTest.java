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
    @Test void corruptCounterDoesNotPreventStartupOrEraseHistory() throws Exception {
        for (String content : new String[]{"corrupt", "-1", "9223372036854775808"}) {
            var file = directory.resolve("visits.txt");
            java.nio.file.Files.writeString(file, content);
            var counter = new VisitsController(directory.toString(), ORIGIN);
            assertEquals(503, assertThrows(ResponseStatusException.class, counter::count).getStatusCode().value());
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> counter.register(ORIGIN,
                    new VisitsController.Visit(UUID.randomUUID().toString()))).getStatusCode().value());
            assertEquals(content, java.nio.file.Files.readString(file));
        }
    }
}
