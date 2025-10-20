package com.gemmap.spot.config.s3;

import com.gemmap.spot.image.infrastructure.objectstorage.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class ObjectStorageConfig {

    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Properties.getCredentials().getAccessKey(),
                s3Properties.getCredentials().getSecretKey()
        );

        String serviceEndpoint = s3Properties.getEndpoint();

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(s3Properties.isPathStyleAccessEnabled())
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(serviceEndpoint))
                .region(Region.of(s3Properties.getRegion().getName()))
                .serviceConfiguration(serviceConfiguration)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
