package com.gemmap.spot.image.infrastructure.objectstorage;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "s3")
public class S3Properties {

    private Credentials credentials = new Credentials();
    private Region region = new Region();
    @NotEmpty
    private String endpoint;
    @NotEmpty
    private String tenantId;
    @NotEmpty
    private String bucket;
    private String basePath;
    private boolean pathStyleAccessEnabled = true;

    @Getter
    @Setter
    public static class Credentials {
        @NotEmpty
        private String accessKey;
        @NotEmpty
        private String secretKey;
    }

    @Getter
    @Setter
    public static class Region {
        @NotEmpty
        private String name;
    }
}
