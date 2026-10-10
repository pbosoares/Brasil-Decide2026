package br.com.brasildecide;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"tse.enabled=false", "tse.snapshot-directory=target/api-test-data"})
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    @Test void emptyApplicationNeverManufacturesResults() throws Exception {
        mvc.perform(get("/api/v1/results/national")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/elections/current")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/results/states")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/results/states/SP")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/results/states/ZZ")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/results/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.desatualizado").value(true)).andExpect(jsonPath("$.fonte").value("TSE"));
        mvc.perform(get("/api/v1/results/manifest.json")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.versao").isEmpty()).andExpect(jsonPath("$.arquivo").isEmpty())
                .andExpect(jsonPath("$.status.estado").value("AGUARDANDO_FONTE"));
        mvc.perform(get("/api/v1/results/snapshots/missing.json")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
