package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.bookmark.application.service.BookmarkService;
import com.gemmap.gemmap.image.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.image.infrastructure.objectstorage.S3UrlGenerator;
import com.gemmap.gemmap.checkin.application.service.CheckinService;
import com.gemmap.gemmap.checkin.domain.repository.SpotCheckinRepository;
import com.gemmap.gemmap.shared.common.enums.ERecommendationLevel;
import com.gemmap.gemmap.shared.common.enums.EAttractionLevel;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.util.SpatialUtils;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.dto.response.*;
import com.gemmap.gemmap.spot.application.support.SpotFileValidator;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BookmarkService bookmarkService;
    private final CheckinService checkinService;
    private final SpotRepository spotRepository;
    private final UserRepository userRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final ObjectStorageService objectStorageService;
    private final S3UrlGenerator s3UrlGenerator;
    private final S3Properties s3Properties;
    private final SpotFileValidator spotFileValidator;

    @Value("${s3.base-path}")
    private String basePath;

    /**
     * 스팟 생성
     *
     * @param userId 요청 사용자 ID
     * @param req 스팟 생성 요청 DTO
     * @param file 업로드할 이미지 파일
     * @return SpotCreateResponse
     */
    @Transactional
    public SpotCreateResponse create(Long userId, SpotCreateRequest req, MultipartFile file) {
        // 1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 2) 요청 검증 (빠른 실패 우선: 좌표 검증 -> 파일 검증)
        validateCoordinates(req.latitude(), req.longitude());
        spotFileValidator.validateImage(file);

        // 3) 업로드 (키 규칙 예: spots/yyyy/MM/uuid.ext)
        String key = buildSpotKey(file.getOriginalFilename());
        String fileUrl;
        try {
            // 기존 ObjectStorageService 사용
            objectStorageService.upload(s3Properties.getBucket(), key, file);
            fileUrl = s3UrlGenerator.generateUrl(key);
        } catch (Exception e) {
            log.error("S3 upload failed for key: {}", key, e);
            throw new CommonException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        try {
            // 4) spots 저장
            Spot spot = spotRepository.save(
                Spot.builder()
                    .user(user)
                    .sido(req.sido())
                    .sigungu(req.sigungu())
                    .eupmyeondong(req.eupmyeondong())
                    .roadName(req.roadName())
                    .buildingNo(req.buildingNo())
                    .fullAddress(req.fullAddress())
                    .shortAddress(req.sido() + " " + req.sigungu()) // 예: 경기도 남양주시 (리스트 화면용)
                    .alias(req.alias())
                    .build()
            );

            // 5) spot_photos 저장 (type=SPOT, fileUrl 직접 저장, user 저장)
            SpotPhoto photo = SpotPhoto.builder()
                .spot(spot)
                .user(user)
                .fileUrl(fileUrl)
                .type(ESpotPhotoType.SPOT)
                .takenAt(parseTakenAt(req.takenAt()))
                .latitude(req.latitude())
                .longitude(req.longitude())
                .location(SpatialUtils.createPoint(req.longitude(), req.latitude()))
                .cameraMake(req.cameraMake())
                .cameraModel(req.cameraModel())
                .focalLength(req.focalLength())
                .aperture(req.aperture())
                .iso(req.iso())
                .shutterSpeed(req.shutterSpeed())
                .build();
            spotPhotoRepository.save(photo);

            // 6) 응답
            return SpotCreateResponse.builder()
                .spotId(spot.getId())
                .fileUrl(fileUrl)
                .build();
        } catch (RuntimeException ex) {
            // 보상 트랜잭션: DB 저장 실패 시 업로드된 S3 객체 삭제
            safeDeleteObject(key);
            throw ex;
        }
    }

    private void validateCoordinates(BigDecimal lat, BigDecimal lon) {
        if (lat != null && (lat.compareTo(BigDecimal.valueOf(-90)) < 0
                || lat.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "위도는 -90~90 범위여야 합니다.");
        }
        if (lon != null && (lon.compareTo(BigDecimal.valueOf(-180)) < 0
                || lon.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "경도는 -180~180 범위여야 합니다.");
        }
    }

    /**
     * 촬영 시각 문자열을 LocalDateTime(KST)으로 파싱
     *
     * <p>타임존 오프셋이 포함된 ISO 8601 형식을 권장합니다.</p>
     *
     * <h3>지원 형식 (우선순위 순):</h3>
     * <ol>
     *   <li><b>오프셋 포함 (권장)</b>: "2025-11-01T14:51:24+09:00" → KST 변환하여 저장</li>
     *   <li><b>UTC (Z suffix)</b>: "2025-11-01T05:51:24Z" → KST 변환하여 저장 (+9시간)</li>
     *   <li><b>오프셋 없음 (Fallback)</b>: "2025-11-01T14:51:24" → KST로 가정하여 저장</li>
     * </ol>
     *
     * <h3>클라이언트 구현 가이드:</h3>
     * <ul>
     *   <li>EXIF 촬영 시각 추출 시, 가능하면 OffsetTimeOriginal 태그를 함께 확인하세요.</li>
     *   <li>타임존 정보가 있으면 ISO 8601 오프셋 형식으로 전송하세요. (예: +09:00, +01:00)</li>
     *   <li>타임존 정보가 없으면 로컬 시간 그대로 전송하세요. (서버에서 KST로 가정 처리)</li>
     * </ul>
     *
     * <h3>변환 예시:</h3>
     * <pre>
     * 입력: "2025-11-01T10:00:00+01:00" (파리, UTC+1)
     * 절대 시간: 2025-11-01T09:00:00Z (UTC)
     * KST 변환: 2025-11-01T18:00:00 (UTC+9)
     * DB 저장: 2025-11-01 18:00:00
     * </pre>
     *
     * @param takenAt ISO 8601 형식의 촬영 시각 문자열 (null 허용)
     * @return KST 기준 LocalDateTime, 입력이 null/blank이면 null
     * @throws CommonException takenAt 형식이 올바르지 않은 경우
     */
    private static LocalDateTime parseTakenAt(String takenAt) {
        if (takenAt == null || takenAt.isBlank()) return null;

        try {
            // 1. 타임존/오프셋 정보가 포함된 경우: OffsetDateTime으로 파싱 후 KST 변환
            if (hasTimezoneInfo(takenAt)) {
                OffsetDateTime odt = OffsetDateTime.parse(takenAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                // 절대 시간(Instant)은 유지하고 표기만 KST로 변환
                return odt.atZoneSameInstant(KST).toLocalDateTime();
            }

            // 2. 타임존 정보가 없는 경우 (Fallback): KST로 가정하여 처리
            // 주의: 해외에서 촬영한 사진의 경우 실제 시간과 다를 수 있음
            log.debug("takenAt에 타임존 정보 없음. KST로 가정하여 처리: {}", takenAt);
            return LocalDateTime.parse(takenAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        } catch (DateTimeParseException e) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE,
                "takenAt 형식이 올바르지 않습니다. ISO 8601 형식을 사용하세요. " +
                "(권장: 2025-11-01T14:51:24+09:00, 허용: 2025-11-01T14:51:24Z, 2025-11-01T14:51:24)");
        }
    }

    /**
     * 문자열에 타임존/오프셋 정보가 포함되어 있는지 확인
     *
     * @param dateTimeStr ISO 8601 형식의 날짜/시간 문자열
     * @return 타임존 정보 포함 여부
     */
    private static boolean hasTimezoneInfo(String dateTimeStr) {
        // Z (UTC), + (양수 오프셋), 또는 T 이후의 - (음수 오프셋) 확인
        // 예: 2025-11-01T14:51:24Z, 2025-11-01T14:51:24+09:00, 2025-11-01T14:51:24-05:00
        if (dateTimeStr.endsWith("Z")) return true;
        if (dateTimeStr.contains("+")) return true;

        // T 이후에 -가 있으면 오프셋 (날짜 부분의 -와 구분)
        int tIndex = dateTimeStr.indexOf('T');
        if (tIndex > 0) {
            String timePart = dateTimeStr.substring(tIndex);
            return timePart.contains("-");
        }
        return false;
    }

    private String buildSpotKey(String originalName) {
        String ext = Optional.ofNullable(originalName)
            .filter(n -> n.contains("."))
            .map(n -> n.substring(n.lastIndexOf('.')))
            .orElse(".jpg");
        String ym = DateTimeFormatter.ofPattern("yyyy/MM").format(LocalDate.now(KST));
        return basePath + ym + "/" + UUID.randomUUID() + ext;
    }

    private void safeDeleteObject(String key) {
        try {
            objectStorageService.delete(s3Properties.getBucket(), key);
            log.info("Compensated: deleted S3 object {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {} during compensation", key, e);
        }
    }

    /**
     * 스팟 삭제 (DB 우선 삭제 후 Object Storage 삭제)
     *
     * @param userId 요청 사용자 ID
     * @param spotId 삭제할 스팟 ID
     */
    @Transactional
    public void delete(Long userId, Long spotId) {
        // 1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 2) Spot 존재 확인
        Spot spot = spotRepository.findById(spotId)
            .orElseThrow(() -> new CommonException(ErrorCode.SPOT_NOT_FOUND));

        // 3) 소유권 검증
        if (!spot.getUser().equals(user)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "해당 스팟을 삭제할 권한이 없습니다.");
        }

        // 4) 연결된 SpotPhoto 조회 (fileUrl 추출용)
        List<SpotPhoto> photos = spotPhotoRepository.findBySpot(spot);
        List<String> fileUrls = photos.stream()
            .map(SpotPhoto::getFileUrl)
            .toList();

        // 5) DB 삭제
        spotRepository.delete(spot);

        // 6) Object Storage 삭제 (트랜잭션 외부, 베스트 에포트)
        for (String fileUrl : fileUrls) {
            safeDeleteObjectByUrl(fileUrl);
        }
    }

    /**
     * Object Storage 파일 삭제 (베스트 에포트, 실패 시 로그만)
     */
    private void safeDeleteObjectByUrl(String fileUrl) {
        try {
            String key = s3UrlGenerator.extractKeyFromUrl(fileUrl);
            objectStorageService.delete(s3Properties.getBucket(), key);
            log.info("Successfully deleted S3 object. URL: {}", fileUrl);
        } catch (Exception e) {
            // 스토리지 삭제 실패는 로그만 남기고 계속 진행 (비용 이슈지만 참조 깨짐 없음)
            log.error("Failed to delete S3 object (best-effort). URL: {}", fileUrl, e);
        }
    }

    /**
     * 스팟 상세 조회
     *
     * @param userId 요청 사용자 ID (인증용, 현재는 조회만 수행)
     * @param spotId 조회할 스팟 ID
     * @return SpotDetailResponse
     */
    @Transactional(readOnly = true)
    public SpotDetailResponse getSpotDetail(Long userId, Long spotId) {
        // 1) 사용자 조회 (인증 확인)
        userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 2) Spot 조회
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CommonException(ErrorCode.SPOT_NOT_FOUND));

        // 3) Spot 작성자(소유자) 조회
        User spotOwner = userRepository.findById(spot.getUser().getId())
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND, "스팟 등록자를 찾을 수 없습니다."));

        // 4) 대표 사진 조회 (최신 1건)
        SpotPhoto representativePhoto = spotPhotoRepository.
                findFirstBySpotAndTypeOrderByCreatedAtDesc(spot, ESpotPhotoType.SPOT)
                .orElseThrow(() -> new CommonException(ErrorCode.SPOT_PHOTO_NOT_FOUND, "스팟 사진이 존재하지 않습니다."));

        // 5) 이 스팟(젬)을 찜한 총 개수 조회
        Integer bookmarkedCount = bookmarkService.getBookmarkedCountBySpot(spotId);

        // 6) 끌림지수(나의 평가) 조회 - 찜하기 하지 않은 경우 null
        EAttractionLevel attractionLevel = bookmarkService.getAttractionLevel(userId, spotId);

        // 7) 이 스팟(젬)을 체크인한 총 개수 조회
        Integer checkedInCount = checkinService.getCheckinCountBySpot(spotId);

        // 8) 추천지수(나의 평가) 조회 - 체크인 하지 않은 경우 null
        ERecommendationLevel recommendationLevel = checkinService.getRecommendationLevel(userId, spotId);

        // 9) 응답 DTO 변환
        return SpotDetailResponse.from(spot, spotOwner, representativePhoto,
                bookmarkedCount, checkedInCount, attractionLevel, recommendationLevel);
    }

    /**
     * 마이 스팟 조회 (사용자 정보 + 카운트: 제보/찜)
     */
    @Transactional(readOnly = true)
    public MySpotsResponse getMySpots(Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 제보한 젬 개수
        Integer createdCount = spotRepository.countByUser(user);
        // 찜한 젬 개수
        Integer bookmarkedCount = bookmarkService.getBookmarkedCount(userId);
        // 체크인한 젬 개수
        Integer checkedInCount = checkinService.getCheckinCount(userId);

        return MySpotsResponse.of(user, createdCount, bookmarkedCount, checkedInCount);
    }

    /**
     * 제보한 젬 목록 조회
     */
    @Transactional(readOnly = true)
    public MyCreatedSpotsResponse getMyCreatedSpots(Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 사용자가 등록한 스팟 목록 조회 (최신순)
        List<Spot> spots = spotRepository.findByUserOrderByCreatedAtDesc(user);

        // 각 스팟의 대표 사진 URL 조회 -> SpotSummary 변환
        List<SpotSummary> spotSummaries = spots.stream()
                .map(spot -> {
                    String fileUrl = spotPhotoRepository
                            .findFirstBySpotAndUserAndTypeOrderByCreatedAtDesc(spot, user, ESpotPhotoType.SPOT)
                            .map(SpotPhoto::getFileUrl)
                            .orElse(null);
                    return SpotSummary.of(spot, fileUrl);
                })
                .toList();

        return MyCreatedSpotsResponse.of(spotSummaries);
    }

    /**
     * 지도 전체 마커 조회
     *
     * @param userId 인증된 사용자 ID
     * @return SpotsResponse
     */
    @Transactional(readOnly = true)
    public SpotsResponse getSpots(Long userId) {
        // 1) 사용자 조회 (인증 확인)
        userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 2) 전체 스팟과 최신 사진 위치 정보를 한 번의 쿼리로 조회 (N+1 문제 해결)
        //    JOIN 쿼리를 사용하여 각 스팟의 가장 최근 SPOT 타입 사진의 위도/경도 정보를 함께 조회
        List<SpotMakerInfo> spotMakers = spotRepository.findAllSpotsWithLatestPhotoLocation(ESpotPhotoType.SPOT);

        // 3) 응답 DTO 반환 - spotId(spots), latitude(spot_photos), longitude(spot_photos)
        return SpotsResponse.of(spotMakers);
    }
}
