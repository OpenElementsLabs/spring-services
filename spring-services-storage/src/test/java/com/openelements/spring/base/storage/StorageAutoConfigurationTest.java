package com.openelements.spring.base.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.file.FileObjectStore;
import com.openelements.spring.base.services.storage.memory.InMemoryObjectStore;
import com.openelements.spring.base.services.storage.s3.S3ObjectStore;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Activation and selection tests for {@link StorageAutoConfiguration}.
 *
 * <p>The module ships three {@link ObjectStore} implementations, so the interesting behaviour is not
 * "does it activate" but "does exactly the configured one activate" — and that nothing activates
 * when an application has the module on the classpath without asking for a store.
 */
@DisplayName("StorageAutoConfiguration")
class StorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class));

    @Test
    @DisplayName("Registers no store when openelements.storage.type is unset")
    void inertWithoutType() {
        contextRunner.run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(ObjectStore.class));
    }

    @Nested
    @DisplayName("selection")
    class Selection {

        @Test
        @DisplayName("type=memory registers the in-memory store")
        void memory() {
            contextRunner.withPropertyValues("openelements.storage.type=memory")
                    .run(context -> assertThat(context).hasNotFailed()
                            .hasSingleBean(ObjectStore.class)
                            .getBean(ObjectStore.class)
                            .isInstanceOf(InMemoryObjectStore.class));
        }

        @Test
        @DisplayName("type=file registers the file store under the configured root")
        void file(@TempDir final Path root) {
            contextRunner.withPropertyValues(
                            "openelements.storage.type=file",
                            "openelements.storage.file.root=" + root)
                    .run(context -> {
                        assertThat(context).hasNotFailed()
                                .hasSingleBean(ObjectStore.class)
                                .getBean(ObjectStore.class)
                                .isInstanceOf(FileObjectStore.class);
                        assertThat(root.resolve("objects")).exists();
                    });
        }

        @Test
        @DisplayName("type=s3 registers the S3 store and its client")
        void s3() {
            contextRunner.withPropertyValues(s3Properties())
                    .run(context -> assertThat(context).hasNotFailed()
                            .hasSingleBean(S3Client.class)
                            .hasSingleBean(ObjectStore.class)
                            .getBean(ObjectStore.class)
                            .isInstanceOf(S3ObjectStore.class));
        }

        @Test
        @DisplayName("type=s3 works without a region, which only AWS derives meaning from")
        void s3WithoutARegion() {
            contextRunner.withPropertyValues(
                            "openelements.storage.type=s3",
                            "openelements.storage.s3.endpoint=https://fsn1.your-objectstorage.com",
                            "openelements.storage.s3.bucket=objects",
                            "openelements.storage.s3.access-key=key",
                            "openelements.storage.s3.secret-key=secret")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(ObjectStore.class);
                        assertThat(context.getBean(StorageProperties.class).requiredS3().region())
                                .isEqualTo(StorageProperties.S3.DEFAULT_REGION);
                    });
        }

        @Test
        @DisplayName("A configured region is kept, for the providers that do care")
        void s3WithAnExplicitRegion() {
            contextRunner.withPropertyValues(s3Properties())
                    .run(context -> assertThat(context).hasNotFailed()
                            .getBean(StorageProperties.class)
                            .extracting(properties -> properties.requiredS3().region())
                            .isEqualTo("eu-central-1"));
        }

        @Test
        @DisplayName("Only the selected store is registered, not the other two")
        void onlyTheSelectedOne() {
            contextRunner.withPropertyValues("openelements.storage.type=memory")
                    .run(context -> assertThat(context).hasNotFailed()
                            .doesNotHaveBean(FileObjectStore.class)
                            .doesNotHaveBean(S3ObjectStore.class)
                            .doesNotHaveBean(S3Client.class));
        }
    }

    @Nested
    @DisplayName("misconfiguration fails start-up")
    class Misconfiguration {

        @Test
        @DisplayName("type=s3 without any openelements.storage.s3.* property")
        void s3WithoutSettings() {
            contextRunner.withPropertyValues("openelements.storage.type=s3")
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure()
                            .rootCause()
                            .hasMessageContaining("openelements.storage.s3.*"));
        }

        @Test
        @DisplayName("type=s3 with an incomplete openelements.storage.s3.* block")
        void s3WithIncompleteSettings() {
            contextRunner.withPropertyValues(
                            "openelements.storage.type=s3",
                            "openelements.storage.s3.endpoint=https://s3.example.com")
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure()
                            .rootCause()
                            .hasMessageContaining("openelements.storage.s3.bucket is required"));
        }

        @Test
        @DisplayName("type=file without openelements.storage.file.root")
        void fileWithoutRoot() {
            contextRunner.withPropertyValues("openelements.storage.type=file")
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure()
                            .rootCause()
                            .hasMessageContaining("openelements.storage.file.root"));
        }
    }

    @Test
    @DisplayName("An application's own ObjectStore wins and the library backs off")
    void consumerBeanWins() {
        contextRunner.withPropertyValues("openelements.storage.type=memory")
                .withBean("applicationStore", ObjectStore.class, ConsumerStore::new)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(ObjectStore.class)
                        .getBean(ObjectStore.class)
                        .isInstanceOf(ConsumerStore.class));
    }

    private static String[] s3Properties() {
        return new String[]{
                "openelements.storage.type=s3",
                "openelements.storage.s3.endpoint=https://s3.example.com",
                "openelements.storage.s3.region=eu-central-1",
                "openelements.storage.s3.bucket=objects",
                "openelements.storage.s3.access-key=key",
                "openelements.storage.s3.secret-key=secret"
        };
    }

    /** Stands in for an application that brings its own store; never called. */
    private static final class ConsumerStore extends InMemoryObjectStore {
    }
}
