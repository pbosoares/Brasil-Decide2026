package br.com.brasildecide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.util.retry.Retry;

@Component
public class TseClient {
    private record Cached(JsonNode body, String etag, String modified) {}
    private final Map<URI, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<URI, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();
    private final WebClient client;
    private final ObjectMapper mapper;
    private final Duration timeout;

    @org.springframework.beans.factory.annotation.Autowired
    public TseClient(TseProperties p, ObjectMapper mapper) {
        this(WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                .followRedirect(false).responseTimeout(p.timeout())))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024)).build(), mapper, p.timeout());
    }
    TseClient(WebClient client, ObjectMapper mapper, Duration timeout) {
        this.client = client; this.mapper = mapper; this.timeout = timeout;
    }
    /** Cache validators advance only after semantic validation. A failed file cannot poison the cache. */
    public JsonNode fetch(URI uri, Consumer<JsonNode> validate) {
        synchronized (locks.computeIfAbsent(uri, ignored -> new Object())) {
            return fetchLocked(uri, validate);
        }
    }
    private JsonNode fetchLocked(URI uri, Consumer<JsonNode> validate) {
        Cached old = cache.get(uri);
        var response = client.get().uri(uri).headers(headers -> {
            if (old != null && old.etag() != null) headers.setIfNoneMatch(old.etag());
            if (old != null && old.modified() != null) headers.set(HttpHeaders.IF_MODIFIED_SINCE, old.modified());
        }).exchangeToMono(r -> {
            if (r.statusCode().value() == 304) {
                if (old == null) return reactor.core.publisher.Mono.error(new IllegalStateException("304 sem cache"));
                return reactor.core.publisher.Mono.just(old);
            }
            if (!r.statusCode().is2xxSuccessful()) return r.createException().flatMap(reactor.core.publisher.Mono::error);
            String etag = r.headers().asHttpHeaders().getETag();
            String modified = r.headers().asHttpHeaders().getFirst(HttpHeaders.LAST_MODIFIED);
            return r.bodyToMono(String.class).switchIfEmpty(reactor.core.publisher.Mono.error(new IllegalArgumentException("Resposta vazia")))
                    .map(body -> {
                        try { return new Cached(mapper.readTree(body), etag, modified); }
                        catch (java.io.IOException ex) { throw new IllegalArgumentException("JSON inválido", ex); }
                    });
        }).timeout(timeout).retryWhen(Retry.backoff(2, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(4))
                .filter(ex -> ex instanceof java.util.concurrent.TimeoutException
                        || ex instanceof org.springframework.web.reactive.function.client.WebClientRequestException
                        || ex instanceof WebClientResponseException http && http.getStatusCode().is5xxServerError()))
                .block(timeout.multipliedBy(4).plusSeconds(10));
        if (response == null) throw new IllegalArgumentException("Resposta ausente");
        validate.accept(response.body());
        cache.put(uri, response);
        return response.body();
    }
}
