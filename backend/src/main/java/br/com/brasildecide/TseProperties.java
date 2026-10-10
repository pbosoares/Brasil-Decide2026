package br.com.brasildecide;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("tse")
public record TseProperties(@NotNull URI baseUrl, @NotNull String environment,
        @NotNull LocalDate electionDate, @Min(1) int round, boolean simulation,
        boolean enabled, @Min(15000) long pollDelayMs, @NotNull Duration timeout, @NotNull Duration staleAfter,
        @NotNull Path snapshotDirectory) {
    public TseProperties {
        if (baseUrl != null && (!"https".equals(baseUrl.getScheme())
                || !"resultados.tse.jus.br".equals(baseUrl.getHost())))
            throw new IllegalArgumentException("Somente a origem HTTPS oficial do TSE é permitida");
        if (environment != null && !environment.equals("oficial"))
            throw new IllegalArgumentException("Este cliente suporta apenas o ambiente oficial");
        if (round != 2) throw new IllegalArgumentException("A aplicação cobre exclusivamente o segundo turno");
        if (electionDate != null && !electionDate.equals(LocalDate.of(2026, 10, 25)))
            throw new IllegalArgumentException("A aplicação cobre exclusivamente 25/10/2026");
        if (simulation) throw new IllegalArgumentException("Simulações não são permitidas neste coletor oficial");
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) throw new IllegalArgumentException("Timeout inválido");
        if (staleAfter != null && (staleAfter.isNegative() || staleAfter.isZero())) throw new IllegalArgumentException("Janela de atraso inválida");
    }
}
