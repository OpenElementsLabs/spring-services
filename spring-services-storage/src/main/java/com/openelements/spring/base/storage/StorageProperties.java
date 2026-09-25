package com.openelements.spring.base.storage;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
     * <p>Endpoint, bucket and credentials are required, and a missing one fails start-up rather than
     * start-up succeeding with a store the application cannot actually write to. The region is the
     * exception — see {@link #region()}.
     *
     * @param endpoint  the endpoint URL, which is explicit because the module targets AWS S3 and
     *                  other S3-compatible providers alike
     * @param region    the region to sign requests for. It belongs to the <em>signature</em>, not to
     *                  the address: SigV4 needs some region string, which is why there is a default,
     *                  but only AWS derives meaning from it. Providers like Hetzner Object Storage
     *                  accept whatever is sent, so configuring it there would be ceremony. Set it
     *                  when your provider cares — on AWS it must match the bucket's region.
     * @param bucket    the bucket every key lives in
     * @param accessKey the access key
     * @param secretKey the secret key
     */
    public record S3(String endpoint, @DefaultValue(DEFAULT_REGION) String region, String bucket,
                     String accessKey, String secretKey) {

        /**
         * The region used when none is configured — the value S3-compatible providers conventionally
         * accept and ignore.
         */
        public static final String DEFAULT_REGION = "us-east-1";

        /**
         * Validates that nothing required is missing.
         *
         * @throws NullPointerException if a required component was not configured
         */
        public S3 {
            required(endpoint, "endpoint");
            required(bucket, "bucket");
            required(accessKey, "access-key");
            required(secretKey, "secret-key");
            // Not "required": the binder fills it from DEFAULT_REGION, so a null here means the
            // record was constructed directly rather than bound, and Region.of would fail later.
            Objects.requireNonNull(region, "region must not be null");
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
