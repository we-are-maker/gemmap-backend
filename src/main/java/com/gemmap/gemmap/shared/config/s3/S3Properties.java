package com.gemmap.gemmap.shared.config.s3;

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

    private Region region = new Region();
    @NotEmpty
    private String bucket;
    private String basePath;

    @Getter
    @Setter
    public static class Region {
        @NotEmpty
        private String name;
    }
}
