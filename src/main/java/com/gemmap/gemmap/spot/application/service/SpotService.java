package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.bookmark.application.service.BookmarkService;
import com.gemmap.gemmap.checkin.application.service.CheckinService;
import com.gemmap.gemmap.checkin.domain.repository.SpotCheckinRepository;
import com.gemmap.gemmap.shared.common.enums.ERecommendationLevel;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3PresignedUrlService;
import com.gemmap.gemmap.shared.common.enums.EAttractionLevel;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.util.DateTimeUtils;
import com.gemmap.gemmap.shared.util.S3FileUtils;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotService {

    private final BookmarkService bookmarkService;
    private final CheckinService checkinService;
    private final SpotRepository spotRepository;
    private final UserRepository userRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final ObjectStorageService objectStorageService;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final S3Properties s3Properties;
    private final SpotFileValidator spotFileValidator;

    @Value("${s3.spot-base-path}")
    private String spotBasePath;

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

        // 2) 사진 정보 활용 동의 확인
        if (!user.hasPhotoConsentAgreed()) {
            throw new CommonException(ErrorCode.PHOTO_CONSENT_REQUIRED);
        }

        // 3) 요청 검증 (빠른 실패 우선: 좌표 검증 -> 파일 검증)
        validateCoordinates(req.latitude(), req.longitude());
        spotFileValidator.validateImage(file);

        // 4) 업로드 (키 규칙 예: spots/yyyy/MM/uuid.ext)
        String key = S3FileUtils.buildKey(spotBasePath, file.getOriginalFilename());
        try {
            // 기존 ObjectStorageService 사용
            objectStorageService.upload(s3Properties.getBucket(), key, file);
        } catch (Exception e) {
            log.error("S3 upload failed for key: {}", key, e);
            throw new CommonException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        try {
            // 5) spots 저장
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

            // 6) spot_photos 저장 (type=SPOT, S3 key 저장, user 저장)
            SpotPhoto photo = SpotPhoto.builder()
                .spot(spot)
                .user(user)
                .fileUrl(key)
                .type(ESpotPhotoType.SPOT)
                .takenAt(DateTimeUtils.parseTakenAt(req.takenAt()))
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

            // 7) 응답 (presigned URL)
            return SpotCreateResponse.builder()
                .spotId(spot.getId())
                .fileUrl(s3PresignedUrlService.generatePresignedGetUrl(key))
                .build();
        } catch (RuntimeException ex) {
            // 보상 트랜잭션: DB 저장 실패 시 업로드된 S3 객체 삭제
            S3FileUtils.safeDelete(objectStorageService, s3Properties, key);
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

        // 4) 연결된 SpotPhoto 조회 (S3 key 추출용)
        List<SpotPhoto> photos = spotPhotoRepository.findBySpot(spot);
        List<String> keys = photos.stream()
            .map(SpotPhoto::getFileUrl)
            .toList();

        // 5) DB 삭제
        spotRepository.delete(spot);

        // 6) Object Storage 삭제 (트랜잭션 외부, 베스트 에포트)
        for (String key : keys) {
            S3FileUtils.safeDelete(objectStorageService, s3Properties, key);
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

        // 9) 응답 DTO 변환 (사진=presigned, 프로필=분기 resolve)
        String photoUrl = s3PresignedUrlService.generatePresignedGetUrl(representativePhoto.getFileUrl());
        String ownerProfileUrl = s3PresignedUrlService.resolveProfileImageUrl(spotOwner.getProfileImage());
        return SpotDetailResponse.from(spot, spotOwner, representativePhoto, photoUrl, ownerProfileUrl,
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

        String profileImageUrl = s3PresignedUrlService.resolveProfileImageUrl(user.getProfileImage());
        return MySpotsResponse.of(user, createdCount, bookmarkedCount, checkedInCount, profileImageUrl);
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
                    String key = spotPhotoRepository
                            .findFirstBySpotAndUserAndTypeOrderByCreatedAtDesc(spot, user, ESpotPhotoType.SPOT)
                            .map(SpotPhoto::getFileUrl)
                            .orElse(null);
                    String fileUrl = (key != null) ? s3PresignedUrlService.generatePresignedGetUrl(key) : null;
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
