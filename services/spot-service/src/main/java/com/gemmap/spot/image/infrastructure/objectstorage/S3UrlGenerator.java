package com.gemmap.spot.image.infrastructure.objectstorage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3UrlGenerator {

    private final S3Properties s3Properties;

    /**
     * 공개 접근 URL을 생성
     * @param key S3 객체 키
     * @return 완전한 파일 URL
     */
    public String generateUrl(String key) {
        return s3Properties.getEndpoint() + "/v1/AUTH_" + s3Properties.getTenantId() + "/" + s3Properties.getBucket() + "/" + key;
    }

    /**
     * 전체 URL에서 S3 객체 키를 추출
     * @param fileUrl 완전한 파일 URL
     * @return S3 객체 키
     */
    public String extractKeyFromUrl(String fileUrl) {
        String prefix = s3Properties.getEndpoint() + "/v1/AUTH_" + s3Properties.getTenantId() + "/" + s3Properties.getBucket() + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        throw new IllegalArgumentException("유효하지 않은 S3 URL 형식입니다: " + fileUrl);
    }
}
