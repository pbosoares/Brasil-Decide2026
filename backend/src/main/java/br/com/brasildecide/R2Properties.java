package br.com.brasildecide;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("publication.r2")
public record R2Properties(boolean enabled, String accountId, String bucket, String accessKeyId,
        String secretAccessKey, String prefix, Duration timeout) {
    public R2Properties {
        if (enabled) {
            if (accountId == null || !accountId.matches("[a-fA-F0-9]{32}")) throw new IllegalArgumentException("Conta R2 inválida");
            if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")) throw new IllegalArgumentException("Bucket R2 inválido");
            if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank())
                throw new IllegalArgumentException("Credenciais R2 ausentes");
        }
        if (prefix == null || !prefix.matches("(?:[a-zA-Z0-9_-]+/)*")) throw new IllegalArgumentException("Prefixo R2 inválido");
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Timeout R2 inválido");
    }
    public URI endpoint() { return URI.create("https://" + accountId + ".r2.cloudflarestorage.com"); }
    @Override public String toString() { return "R2Properties[enabled=" + enabled + ", credentials=REDACTED]"; }
}
