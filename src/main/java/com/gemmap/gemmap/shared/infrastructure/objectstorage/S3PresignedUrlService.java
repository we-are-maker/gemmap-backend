package com.gemmap.gemmap.shared.infrastructure.objectstorage;

import com.gemmap.gemmap.shared.config.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private static final Duration PRESIGNED_URL_TTL = Duration.ofHours(1); // 프로젝트 확정값
    private static final String PROFILE_KEY_PREFIX = "profiles/";

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    /**
     * S3 객체 key로 Presigned GET URL을 생성한다. 유효 시간 1시간. 항상 key를 받는다.
     *
     * @param key S3 객체 키 (예: dev/spots/2026/05/uuid.jpg)
     */
    public String generatePresignedGetUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        log.debug("Generated presigned URL. key={}, ttl={}h", key, PRESIGNED_URL_TTL.toHours());
        return url;
    }

    /**
     * 프로필 이미지 분기 변환.
     * - profiles/ prefix → presign
     * - 그 외(소셜 외부 URL http(s)://, default.png, null/blank) → 그대로
     * <p>
     * NOTE: 프로필 키는 AuthService.generateProfileImageKey()가 "profiles/" 고정 생성
     * (S3FileUtils.buildKey의 base-path 미적용)이라 환경별(dev/local) 변형이 없다.
     * 키 생성 규칙이 바뀌면 PROFILE_KEY_PREFIX도 함께 갱신해야 한다.
     */
    public String resolveProfileImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.startsWith(PROFILE_KEY_PREFIX)) {
            return generatePresignedGetUrl(value);
        }
        return value;
    }
}
