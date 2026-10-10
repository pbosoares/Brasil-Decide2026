package br.com.brasildecide;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

final class R2Storage implements ObjectStorage {
    private final S3Client client;
    private final R2Properties properties;
    R2Storage(S3Client client, R2Properties properties) { this.client = client; this.properties = properties; }
    @Override public Optional<Stored> read(String key) {
        try {
            var body = client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return Optional.of(new Stored(body.asByteArray(), etag(body.response().eTag())));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return Optional.empty();
            throw ex;
        }
    }
    @Override public String put(String key, byte[] bytes, String cacheControl, String ifMatch, boolean createOnly) {
        if (createOnly && ifMatch != null) throw new IllegalArgumentException("Condições incompatíveis");
        try {
            var request = PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                    .contentType("application/json; charset=utf-8").cacheControl(cacheControl)
                    .contentMD5(Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(bytes)));
            if (createOnly) request.ifNoneMatch("*");
            if (ifMatch != null) request.ifMatch(ifMatch);
            return etag(client.putObject(request.build(), RequestBody.fromBytes(bytes)).eTag());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 412 || ex.statusCode() == 409) throw new Conflict();
            throw ex;
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static String etag(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Resposta R2 sem ETag");
        return value;
    }
}
