package com.openelements.spring.base.storage;

import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.storage.file.FileObjectStore;
import com.openelements.spring.base.services.storage.memory.InMemoryObjectStore;
import com.openelements.spring.base.services.storage.s3.S3Clients;
import com.openelements.spring.base.services.storage.s3.S3ObjectStore;
import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Auto-configuration for the optional object-store feature.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, but —
 * unlike the other feature modules — being on the classpath is not enough to activate it. The
 * module ships three {@link ObjectStore} implementations and they are all present at once, so
 * {@code @ConditionalOnClass} cannot choose between them and classpath order must not. The choice is
 * {@code openelements.storage.type}, and with it unset nothing here is registered.
 *
 * <p>Each store is guarded by {@link ConditionalOnMissingBean}, so an application that declares its
 * own {@link ObjectStore} keeps it and the library backs off.
 *
 * <p>The beans live on the auto-configuration itself rather than on an imported
 * {@code @Configuration} — the shape the other modules use — because {@code @ConditionalOnMissingBean}
 * is only reliable while auto-configurations are being processed, which is after the application's
 * own beans are known.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "openelements.storage", name = "type")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    /** Creates the auto-configuration; the store is contributed by one of the {@code @Bean} methods. */
    public StorageAutoConfiguration() {
    }

    /**
     * The SDK client the S3 store issues requests through, closed when the context shuts down.
     *
     * <p>Path-style addressing is forced because several S3-compatible providers do not support
     * virtual-host addressing.
     *
     * @param properties the storage configuration
     * @return the client
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openelements.storage", name = "type", havingValue = "s3")
    public S3Client storageS3Client(final StorageProperties properties) {
        final StorageProperties.S3 s3 = properties.requiredS3();
        return S3Clients.withoutChunkedEncoding(S3Client.builder()
                        .endpointOverride(URI.create(s3.endpoint()))
                        .region(Region.of(s3.region()))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()))))
                .forcePathStyle(true)
                .build();
    }

    /**
     * The store for {@code openelements.storage.type=s3}.
     *
     * @param s3Client   the client to issue requests through
     * @param properties the storage configuration
     * @return the store
     */
    @Bean
    @ConditionalOnMissingBean(ObjectStore.class)
    @ConditionalOnProperty(prefix = "openelements.storage", name = "type", havingValue = "s3")
    public ObjectStore s3ObjectStore(final S3Client s3Client, final StorageProperties properties) {
        return new S3ObjectStore(s3Client, properties.requiredS3().bucket());
    }

    /**
     * The store for {@code openelements.storage.type=file}.
     *
     * @param properties the storage configuration
     * @return the store
     */
    @Bean
    @ConditionalOnMissingBean(ObjectStore.class)
    @ConditionalOnProperty(prefix = "openelements.storage", name = "type", havingValue = "file")
    public ObjectStore fileObjectStore(final StorageProperties properties) {
        return new FileObjectStore(properties.requiredFile().root());
    }

    /**
     * The store for {@code openelements.storage.type=memory}, which keeps every object on the heap
     * and loses all of them when the process ends.
     *
     * @return the store
     */
    @Bean
    @ConditionalOnMissingBean(ObjectStore.class)
    @ConditionalOnProperty(prefix = "openelements.storage", name = "type", havingValue = "memory")
    public ObjectStore inMemoryObjectStore() {
        return new InMemoryObjectStore();
    }
}
