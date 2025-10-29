package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {
    List<SpotPhoto> findBySpot(Spot spot);

    /**
     * 특정 스팟의 type이 SPOT인 대표 사진 조회 (최신 1건)
     */
    Optional<SpotPhoto> findFirstBySpotAndTypeOrderByCreatedAtDesc(Spot spot, ESpotPhotoType type);

    /**
     * 특정 스팟 + 특정 사용자 + 특정 타입의 대표 사진 조회 (최신순)
     * 내가 제보한 스팟 목록 조회 시 사용
     *
     * @param spot 스팟
     * @param user 사용자
     * @param type 사진 타입
     * @return 대표 사진
     */
    Optional<SpotPhoto> findFirstBySpotAndUserAndTypeOrderByCreatedAtDesc(Spot spot, User user, ESpotPhotoType type);
}
