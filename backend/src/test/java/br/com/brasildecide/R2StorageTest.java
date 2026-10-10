package br.com.brasildecide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class R2StorageTest {
    final S3Client client = mock(S3Client.class);
    final R2Properties properties = new R2Properties(true,"0".repeat(32),"test-bucket","TEST_ACCESS","TEST_SECRET","results/",Duration.ofSeconds(10));
    @Test void sendsJsonCacheHeadersAndConditionalWrites() throws Exception {
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenReturn(PutObjectResponse.builder().eTag("\"ok\"").build());
        var storage = new R2Storage(client,properties); byte[] body = "{\"ok\":true}".getBytes();
        assertEquals("\"ok\"",storage.put("results/manifest.json",body,SnapshotPublisher.MANIFEST_CACHE,"\"previous\"",false));
        var request = ArgumentCaptor.forClass(PutObjectRequest.class); var payload = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(),payload.capture());
        assertEquals("test-bucket",request.getValue().bucket()); assertEquals("application/json; charset=utf-8",request.getValue().contentType());
        assertEquals(SnapshotPublisher.MANIFEST_CACHE,request.getValue().cacheControl());
        assertEquals("\"previous\"",request.getValue().ifMatch()); assertNotNull(request.getValue().contentMD5());
        assertArrayEquals(body,payload.getValue().contentStreamProvider().newStream().readAllBytes());
        storage.put("results/snapshots/v1.json",body,SnapshotPublisher.SNAPSHOT_CACHE,null,true);
        verify(client).putObject(argThat((PutObjectRequest r) -> "*".equals(r.ifNoneMatch()) && r.ifMatch() == null),any(RequestBody.class));
    }
    @Test void returnsBodyAndEtagAndDistinguishesMissingFromUnauthorized() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().eTag("\"v1\"").build(),"{}".getBytes()));
        var storage = new R2Storage(client,properties);
        assertEquals("\"v1\"",storage.read("manifest.json").orElseThrow().etag());
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertTrue(storage.read("missing").isEmpty());
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        assertThrows(S3Exception.class,() -> storage.read("forbidden"));
    }
    @Test void preconditionFailureBecomesConflictWithoutLeakingServerDetails() {
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenThrow(S3Exception.builder().statusCode(412).message("sensitive remote detail").build());
        var ex = assertThrows(ObjectStorage.Conflict.class,() -> new R2Storage(client,properties).put("manifest.json","{}".getBytes(),"no-cache","old",false));
        assertFalse(ex.getMessage().contains("sensitive"));
    }
    @Test void credentialsAreRedactedAndInvalidConfigurationFailsBeforeNetworkAccess() {
        assertFalse(properties.toString().contains("TEST_SECRET")); assertFalse(properties.toString().contains("TEST_ACCESS"));
        assertThrows(IllegalArgumentException.class,() -> new R2Properties(true,"invalid","test-bucket","a","b","results/",Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class,() -> new R2Properties(false,"","","","","../",Duration.ofSeconds(10)));
    }
}
