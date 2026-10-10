package br.com.brasildecide;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@ConditionalOnProperty(name = "publication.r2.enabled", havingValue = "true")
class R2Configuration {
    @Bean(destroyMethod = "close") S3Client r2Client(R2Properties p) {
        return S3Client.builder().endpointOverride(p.endpoint()).region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKeyId(), p.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(p.timeout()))
                .overrideConfiguration(c -> c.apiCallTimeout(p.timeout()).apiCallAttemptTimeout(p.timeout()).retryPolicy(r -> r.numRetries(1)))
                .build();
    }
    @Bean ObjectStorage r2Storage(S3Client client, R2Properties properties) { return new R2Storage(client, properties); }
    // Explicit default scheduler keeps publication latency away from TSE collection.
    @Bean ThreadPoolTaskScheduler taskScheduler() { return scheduler("collection-"); }
    @Bean ThreadPoolTaskScheduler publicationScheduler() { return scheduler("publication-"); }
    private ThreadPoolTaskScheduler scheduler(String name) {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix(name);
        return scheduler;
    }
}
