package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.image.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.image.infrastructure.objectstorage.S3UrlGenerator;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotService {

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
            // URL 생성 (NHN Cloud 형식)
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
                    .address(req.address())
                    .alias(req.alias())
                    .build()
            );

            // 5) spot_photos 저장 (type=SPOT, fileUrl 직접 저장, user 저장)
            SpotPhoto photo = SpotPhoto.builder()
                .spot(spot)
                .user(user)
                .fileUrl(fileUrl)
                .type(ESpotPhotoType.SPOT)
                .takenAt(parseUtc(req.takenAt()))
                .latitude(req.latitude())
                .longitude(req.longitude())
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

    private static LocalDateTime parseUtc(String isoZ) {
        if (isoZ == null || isoZ.isBlank()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(isoZ), ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "takenAt은 UTC ISO8601 형식이어야 합니다.");
        }
    }

    private String buildSpotKey(String originalName) {
        String ext = Optional.ofNullable(originalName)
            .filter(n -> n.contains("."))
            .map(n -> n.substring(n.lastIndexOf('.')))
            .orElse(".jpg");
        String ym = DateTimeFormatter.ofPattern("yyyy/MM").format(LocalDate.now(ZoneOffset.UTC));
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

        // 5) DB 삭제 (트랜잭션 내): spot_photos → spots 순서 (cascade 없으므로 명시 삭제)
        spotPhotoRepository.deleteAll(photos);
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

        // 5) 응답 DTO 변환
        return SpotDetailResponse.from(spot, spotOwner, representativePhoto);
    }

    /**
     * 내가 제보한 스팟 목록 조회
     *
     * @param userId 요청 사용자 ID
     * @return MySpotsResponse
     */
    @Transactional(readOnly = true)
    public MySpotsResponse getMySpots(Long userId) {
        // 1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 2) 사용자가 등록한 스팟 전체 개수 조회
        Integer totalCount = spotRepository.countByUser(user);

        // 3) 사용자가 등록한 스팟 목록 조회 (최신순)
        List<Spot> spots = spotRepository.findByUserOrderByCreatedAtDesc(user);

        // 4) 각 스팟에 대한 사진(type=SPOT, user=요청 사용자) 조회 후 DTO 변환
        //    - 사진 없으면 해당 스팟은 노출하되 fileUrl = null
        //    - 스팟 목록 자체가 없으면 자연스럽게 빈 리스트 반환
        // TODO: N+1 문제 보완 가능 - @EntityGraph 또는 Batch Fetch 전략 고려
        // 현재 요구사항에서는 per-spot 1회 조회로 충분하나, 스팟이 많아질 경우 최적화 필요
        List<SpotSummary> spotSummaries = spots.stream()
                .map(spot -> {
                    // 각 spot마다 DB 쿼리 1회 발생 → N+1 문제
                    String fileUrl = spotPhotoRepository
                            .findFirstBySpotAndUserAndTypeOrderByCreatedAtDesc(spot, user, ESpotPhotoType.SPOT)
                            .map(SpotPhoto::getFileUrl)
                            .orElse(null);
                    return SpotSummary.of(spot, fileUrl);
                })
                .collect(Collectors.toList());

        // 5) 응답 DTO 변환
        return MySpotsResponse.of(user, totalCount, spotSummaries);
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
