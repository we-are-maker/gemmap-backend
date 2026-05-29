package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {
    List<SpotPhoto> findBySpot(Spot spot);

    /**
     * 특정 스팟에 속한 모든 SpotPhoto의 fileUrl(S3 키)만 조회한다.
     *
     * SpotPhoto 엔티티를 영속성 컨텍스트에 로드하지 않기 위한 projection 쿼리다.
     * 스팟 삭제 시 S3 객체 정리를 위한 키 수집 용도로만 사용한다.
     */
    @Query("select sp.fileUrl from SpotPhoto sp where sp.spot.id = :spotId")
    List<String> findFileUrlsBySpot(@Param("spotId") Long spotId);

    /**
     * 특정 스팟의 type이 SPOT인 대표 사진 조회 (최신 1건)
     */
    Optional<SpotPhoto> findFirstBySpotAndTypeOrderByCreatedAtDesc(Spot spot, ESpotPhotoType type);

    /**
     * 특정 스팟 + 특정 사용자 + 특정 타입의 대표 사진 조회 (최신순)
     * 내가 제보한 스팟 목록 조회 시 사용
     */
    Optional<SpotPhoto> findFirstBySpotAndUserAndTypeOrderByCreatedAtDesc(Spot spot, User user, ESpotPhotoType type);

    /**
     * Bounding Box 내 마커 조회 (Spatial Index 활용)
     *
     * ST_MakeEnvelope로 사각형 생성 → ST_Contains로 포함 여부 검사.
     * R-Tree 인덱스로 O(log n) 성능.
     */
    @Query(value = """
        SELECT sp.* FROM spot_photos sp
        WHERE sp.type = :type
        AND ST_Contains(
            ST_SRID(ST_MakeEnvelope(
                Point(:swLng, :swLat),
                Point(:neLng, :neLat)
            ), 4326),
            sp.location
        )
        ORDER BY sp.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<SpotPhoto> findWithinBoundingBox(double swLng, double swLat, double neLng, double neLat,
                                          String type, int limit);
}
