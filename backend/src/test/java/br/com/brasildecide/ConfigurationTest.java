package br.com.brasildecide;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.*;
import org.junit.jupiter.api.Test;

class ConfigurationTest {
    static TseProperties properties(Path path) {
        return new TseProperties(URI.create("https://resultados.tse.jus.br"), "oficial", LocalDate.of(2026, 10, 25),
                2, false, true, 30000, Duration.ofSeconds(2), Duration.ofMinutes(2), path);
    }
    @Test void discoversOnlyFederalPresidentialSecondRound() throws Exception {
        var mapper = new ObjectMapper();
        var config = TseConfiguration.discover(mapper.readTree(getClass().getResourceAsStream("/fixtures/ele-c.json")), properties(Path.of("data")));
        assertEquals("6258", config.election().codigo());
        assertEquals(2, config.election().turno().numero());
        assertEquals("https://resultados.tse.jus.br/oficial/ele2026/6258/dados/sp/sp-c0001-e006258-u.json", config.resultUri("sp").toString());
        assertThrows(IllegalArgumentException.class, () -> config.resultUri("../admin"));
    }
    @Test void rejectsUnavailableElection() {
        assertThrows(IllegalArgumentException.class, () -> TseConfiguration.discover(new ObjectMapper().readTree("{\"f\":\"o\",\"arq\":[{\"tp\":\"u\",\"dir\":\"x\"}],\"pl\":[]}"), properties(Path.of("data"))));
    }
}
