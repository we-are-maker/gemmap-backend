package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {
    List<SpotPhoto> findBySpot(Spot spot);

    // 특정 스팟의 type이 SPOT인 대표 사진 조회 (최신 1건)
    Optional<SpotPhoto> findFirstBySpotAndTypeOrderByCreatedAtDesc(Spot spot, ESpotPhotoType type);
}
