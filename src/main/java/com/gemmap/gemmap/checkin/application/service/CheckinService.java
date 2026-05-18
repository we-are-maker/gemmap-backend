package com.gemmap.gemmap.checkin.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.checkin.application.dto.request.CheckinCreateRequest;
import com.gemmap.gemmap.checkin.application.dto.response.CheckinCreateResponse;
import com.gemmap.gemmap.checkin.application.dto.response.MyCheckinSpotsResponse;
import com.gemmap.gemmap.checkin.domain.entity.SpotCheckin;
import com.gemmap.gemmap.checkin.domain.repository.SpotCheckinRepository;
import com.gemmap.gemmap.shared.common.enums.ERecommendationLevel;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3PresignedUrlService;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.util.DateTimeUtils;
import com.gemmap.gemmap.shared.util.S3FileUtils;
import com.gemmap.gemmap.shared.util.SpatialUtils;
import com.gemmap.gemmap.spot.application.dto.response.SpotSummary;
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

import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final SpotRepository spotRepository;
    private final SpotCheckinRepository spotCheckinRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final ObjectStorageService objectStorageService;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final S3Properties s3Properties;
    private final SpotFileValidator spotFileValidator;

    @Value("${s3.checkin-base-path}")
    private String checkinBasePath;

    /**
     * 젬 획득하기 (체크인)
     *
     * 처리 순서:
     * 1) 사용자 조회 → 2) 동의 확인 → 3) 스팟 조회 → 4) 중복 확인 → 5) 파일 검증
     * → 6) S3 업로드 → 7) spot_photos 저장 → 8) spot_checkins 저장 → 9) 응답
     *
     * 위치 검증은 프론트엔드에서 수행. 백엔드는 좌표를 수신하여 spot_photos에 저장만 함.
     */
    @Transactional
    public CheckinCreateResponse createCheckin(Long userId, Long spotId, CheckinCreateRequest req, MultipartFile file) {
        // 1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 2) 사진 정보 활용 동의 확인
        if (!user.hasPhotoConsentAgreed()) {
            throw new CommonException(ErrorCode.PHOTO_CONSENT_REQUIRED);
        }

        // 3) 스팟 조회
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CommonException(ErrorCode.SPOT_NOT_FOUND));

        // 4) 중복 체크인 확인
        if (spotCheckinRepository.existsByUserAndSpot(user, spot)) {
            throw new CommonException(ErrorCode.CHECKIN_ALREADY_EXISTS);
        }

        // 5) 파일 검증
        spotFileValidator.validateImage(file);

        // 6) S3 업로드 (키 규칙 예: spots/yyyy/MM/uuid.ext)
        String key = S3FileUtils.buildKey(checkinBasePath, file.getOriginalFilename());
        try {
            // 기존 ObjectStorageService 사용
            objectStorageService.upload(s3Properties.getBucket(), key, file);
        } catch (Exception e) {
            log.error("S3 upload failed for key: {}", key, e);
            throw new CommonException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        try {
            // 7) spot_photos 저장
            SpotPhoto photo = SpotPhoto.builder()
                    .spot(spot)
                    .user(user)
                    .fileUrl(key)
                    .type(ESpotPhotoType.CHECKIN)
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

            // 8) spot_checkins 저장 (photo_id FK 연결)
            SpotCheckin checkin = SpotCheckin.builder()
                    .user(user)
                    .spot(spot)
                    .photo(photo)
                    .recommendationLevel(req.recommendationLevel())
                    .build();
            spotCheckinRepository.save(checkin);

            // 9) 응답 (presigned URL)
            return CheckinCreateResponse.of(checkin, s3PresignedUrlService.generatePresignedGetUrl(key));
        } catch (RuntimeException ex) {
            // 보상 트랜잭션: DB 저장 실패 시 업로드된 S3 객체 삭제
            S3FileUtils.safeDelete(objectStorageService, s3Properties, key);
            throw ex;
        }
    }

    /**
     * 체크인 취소
     *
     * 처리 순서:
     * 1) spot_checkins 조회 → 2) photo_id로 fileUrl 추출
     * → 3) spot_checkins 삭제 → 4) spot_photos 삭제 → 5) S3 삭제 (베스트 에포트)
     */
    @Transactional
    public void deleteCheckin(Long userId, Long spotId) {
        // 1) 체크인 조회
        SpotCheckin checkin = spotCheckinRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CommonException(ErrorCode.CHECKIN_NOT_FOUND));

        // 2) photo_id FK로 사진 조회 → S3 key 추출
        SpotPhoto photo = checkin.getPhoto();
        String key = photo.getFileUrl();

        // 3) spot_checkins 삭제 (FK 참조 해제)
        spotCheckinRepository.delete(checkin);

        // 4) spot_photos 삭제
        spotPhotoRepository.delete(photo);

        // 5) S3 삭제 (베스트 에포트)
        S3FileUtils.safeDelete(objectStorageService, s3Properties, key);
    }

    /**
     * 스팟의 체크인 개수 조회
     * → SpotService.getSpotDetail() 에서 호출
     */
    @Transactional(readOnly = true)
    public Integer getCheckinCountBySpot(Long spotId) {
        return spotCheckinRepository.countBySpotId(spotId);
    }

    /**
     * 추천지수 조회 (요청 사용자)
     * → SpotService.getSpotDetail() 에서 호출
     *
     * @return 추천지수 (체크인 안 했으면 null)
     */
    @Transactional(readOnly = true)
    public ERecommendationLevel getRecommendationLevel(Long userId, Long spotId) {
        return spotCheckinRepository.findByUserIdAndSpotId(userId, spotId)
                .map(SpotCheckin::getRecommendationLevel)
                .orElse(null);
    }

    /**
     * 사용자가 체크인한 젬 개수 조회
     * → SpotService.getMySpots() 에서 호출
     */
    @Transactional(readOnly = true)
    public Integer getCheckinCount(Long userId) {
        return spotCheckinRepository.countByUserId(userId);
    }

    /**
     * 체크인 젬 목록 조회 (마이스팟)
     * - 응답의 fileUrl은 "체크인에 사용한 사진" URL (SpotPhoto.type = CHECKIN)
     */
    @Transactional(readOnly = true)
    public MyCheckinSpotsResponse getMyCheckinSpots(Long userId) {
        List<SpotCheckin> checkins = spotCheckinRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<SpotSummary> spotSummaries = checkins.stream()
                .map(checkin -> SpotSummary.of(checkin.getSpot(),
                        s3PresignedUrlService.generatePresignedGetUrl(checkin.getPhoto().getFileUrl())))
                .toList();
        return MyCheckinSpotsResponse.of(spotSummaries);
    }

}