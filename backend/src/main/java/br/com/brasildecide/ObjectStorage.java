package br.com.brasildecide;

import java.util.Optional;

interface ObjectStorage {
    record Stored(byte[] bytes, String etag) {}
    Optional<Stored> read(String key);
    String put(String key, byte[] bytes, String cacheControl, String ifMatch, boolean createOnly);
    final class Conflict extends RuntimeException {
        Conflict() { super("Objeto alterado por outro publicador"); }
    }
}
