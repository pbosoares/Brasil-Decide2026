package br.com.brasildecide;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import software.amazon.awssdk.services.s3.S3Client;

class R2ConfigurationTest {
    // No scheduling or requests: construct the real SDK client with test credentials only.
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(R2Properties.class)
    static class PropertiesConfig {}
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, R2Configuration.class)
            .withPropertyValues("publication.r2.prefix=results/", "publication.r2.timeout=10s");
    @Test void disabledPublicationRequiresNoAccountOrCredentials() {
        runner.withPropertyValues("publication.r2.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(S3Client.class).doesNotHaveBean(ObjectStorage.class);
        });
    }
    @Test void enabledPublicationBuildsTheSdkAndUsesSeparateSchedulers() {
        runner.withPropertyValues("publication.r2.enabled=true", "publication.r2.account-id=" + "0".repeat(32),
                "publication.r2.bucket=test-bucket", "publication.r2.access-key-id=TEST_ACCESS", "publication.r2.secret-access-key=TEST_SECRET")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(S3Client.class).hasSingleBean(ObjectStorage.class);
                    var collect = context.getBean("taskScheduler",ThreadPoolTaskScheduler.class);
                    var publish = context.getBean("publicationScheduler",ThreadPoolTaskScheduler.class);
                    assertThat(collect).isNotSameAs(publish);
                    assertThat(collect.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1); assertThat(publish.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
                });
    }
}
