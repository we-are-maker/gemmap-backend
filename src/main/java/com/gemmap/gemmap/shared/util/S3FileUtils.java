package com.gemmap.gemmap.shared.util;

import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3UrlGenerator;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * S3 오브젝트 스토리지 유틸리티
 *
 * S3 객체 키 생성, 보상 트랜잭션용 삭제, 베스트 에포트 삭제를 제공한다.
 * SpotService, CheckinService 등에서 공통으로 사용한다.
 */
@Slf4j
public final class S3FileUtils {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private S3FileUtils() {}

    /**
     * S3 객체 키 생성
     * 키 규칙: {basePath}{yyyy}/{MM}/{uuid}{ext}
     *
     * @param basePath S3Properties.getBasePath() 또는 @Value("${s3.base-path}")
     * @param originalName 원본 파일명 (확장자 추출용)
     */
    public static String buildKey(String basePath, String originalName) {
        String ext = Optional.ofNullable(originalName)
                .filter(n -> n.contains("."))
                .map(n -> n.substring(n.lastIndexOf('.')))
                .orElse(".jpg");
        String ym = DateTimeFormatter.ofPattern("yyyy/MM").format(LocalDate.now(KST));
        return basePath + ym + "/" + UUID.randomUUID() + ext;
    }

    /**
     * S3 객체 삭제 — 보상 트랜잭션용 (key 기반)
     * DB 저장 실패 시 업로드된 S3 객체를 삭제하는 보상 로직
     */
    public static void safeDelete(ObjectStorageService objectStorageService,
                                  S3Properties s3Properties, String key) {
        try {
            objectStorageService.delete(s3Properties.getBucket(), key);
            log.info("Compensated: deleted S3 object {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {} during compensation", key, e);
        }
    }

    /**
     * S3 객체 삭제 — 베스트 에포트 (URL 기반)
     * 체크인 취소/스팟 삭제 시 S3 객체를 삭제하는 베스트 에포트 로직
     */
    public static void safeDeleteByUrl(ObjectStorageService objectStorageService,
                                       S3Properties s3Properties,
                                       S3UrlGenerator s3UrlGenerator,
                                       String fileUrl) {
        try {
            String key = s3UrlGenerator.extractKeyFromUrl(fileUrl);
            objectStorageService.delete(s3Properties.getBucket(), key);
            log.info("Successfully deleted S3 object. URL: {}", fileUrl);
        } catch (Exception e) {
            // 스토리지 삭제 실패는 로그만 남기고 계속 진행 (비용 이슈지만 참조 깨짐 없음)
            log.error("Failed to delete S3 object (best-effort). URL: {}", fileUrl, e);
        }
    }
}