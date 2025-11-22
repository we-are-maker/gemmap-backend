package com.gemmap.gemmap.image.infrastructure.objectstorage;

import com.gemmap.gemmap.shared.config.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3UrlGenerator {

    private final S3Properties s3Properties;

    /**
     * 공개 접근 URL을 생성 (OCI S3 호환 모드)
     * @param key S3 객체 키
     * @return 완전한 파일 URL
     */
    public String generateUrl(String key) {
        // OCI Object Storage S3 호환 URL 패턴: {endpoint}/{bucket}/{key}
        return s3Properties.getEndpoint() + "/" + s3Properties.getBucket() + "/" + key;
    }

    /**
     * 전체 URL에서 S3 객체 키를 추출 (OCI S3 호환 모드)
     * @param fileUrl 완전한 파일 URL
     * @return S3 객체 키
     */
    public String extractKeyFromUrl(String fileUrl) {
        // OCI Object Storage S3 호환 URL 패턴: {endpoint}/{bucket}/{key}
        String prefix = s3Properties.getEndpoint() + "/" + s3Properties.getBucket() + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }
        throw new IllegalArgumentException("유효하지 않은 S3 URL 형식입니다: " + fileUrl);
    }
}
