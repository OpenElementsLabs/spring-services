package com.openelements.spring.base.storage;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the object store, bound from the {@code openelements.storage.*} namespace.
 *
 * <p>{@link #type()} has no default. The module ships three implementations and no rule could pick
 * between them: they are all on the classpath at once, so {@code @ConditionalOnClass} — the way the
 * other feature modules decide — cannot tell them apart. Leaving the property unset therefore
 * registers nothing at all, and an application that wants a store says which one.
 *
 * @param type which implementation to register, or {@code null} to register none
 * @param s3   settings for {@link Type#S3}; required for that type, ignored otherwise
 * @param file settings for {@link Type#FILE}; required for that type, ignored otherwise
 */
@ConfigurationProperties("openelements.storage")
public record StorageProperties(
        @Nullable Type type,
        @Nullable S3 s3,
        @Nullable File file
) {

    /** The implementation an application selects through {@code openelements.storage.type}. */
    public enum Type {

        /** {@link com.openelements.spring.base.services.storage.s3.S3ObjectStore} over an S3-compatible endpoint. */
        S3,

        /** {@link com.openelements.spring.base.services.storage.file.FileObjectStore} over a local directory. */
        FILE,

        /**
         * {@link com.openelements.spring.base.services.storage.memory.InMemoryObjectStore}, which keeps
         * every object on the heap and loses all of them when the process ends. For tests and local
         * development; never for an environment whose objects have to outlive a restart.
         */
        MEMORY
    }

    /**
     * The S3 settings, which {@code openelements.storage.type=s3} requires.
     *
     * @return the settings
     * @throws NullPointerException if no {@code openelements.storage.s3.*} property is configured
     */
    public S3 requiredS3() {
        return Objects.requireNonNull(s3,
                "openelements.storage.s3.* must be configured when openelements.storage.type=s3");
    }

    /**
     * The file-system settings, which {@code openelements.storage.type=file} requires.
     *
     * @return the settings
     * @throws NullPointerException if no {@code openelements.storage.file.*} property is configured
     */
    public File requiredFile() {
        return Objects.requireNonNull(file,
                "openelements.storage.file.root must be configured when openelements.storage.type=file");
    }

    /**
     * Connection settings for an S3-compatible endpoint. Credentials belong in environment variables
     * or a secret manager, never in a checked-in properties file.
     *
     * <p>Every component is required, and a missing one fails start-up rather than start-up
     * succeeding with a store the application cannot actually write to.
     *
     * @param endpoint  the endpoint URL, which is explicit because the module targets AWS S3 and
     *                  other S3-compatible providers alike
     * @param region    the region to sign requests for
     * @param bucket    the bucket every key lives in
     * @param accessKey the access key
     * @param secretKey the secret key
     */
    public record S3(String endpoint, String region, String bucket, String accessKey,
                     String secretKey) {

        /**
         * Validates that nothing is missing.
         *
         * @throws NullPointerException if any component was not configured
         */
        public S3 {
            required(endpoint, "endpoint");
            required(region, "region");
            required(bucket, "bucket");
            required(accessKey, "access-key");
            required(secretKey, "secret-key");
        }

        private static void required(final @Nullable String value, final String name) {
            Objects.requireNonNull(value, () -> "openelements.storage.s3." + name + " is required");
        }
    }

    /**
     * Settings for the file-system store.
     *
     * @param root the directory the store owns; created if missing
     */
    public record File(Path root) {

        /**
         * Validates that the root is configured.
         *
         * @throws NullPointerException if {@code root} was not configured
         */
        public File {
            Objects.requireNonNull(root, "openelements.storage.file.root is required");
        }
    }
}
