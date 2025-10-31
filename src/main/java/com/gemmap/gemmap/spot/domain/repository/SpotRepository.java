package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.spot.application.dto.response.SpotMakerInfo;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    /**
     * 특정 사용자가 생성한 스팟 목록 조회 (최신순)
     *
     * @param user 사용자
     * @return 스팟 목록
     */
    List<Spot> findByUserOrderByCreatedAtDesc(User user);

    /**
     * 특정 사용자가 생성한 스팟 개수 조회
     *
     * @param user 사용자
     * @return 스팟 개수
     */
    Integer countByUser(User user);

    /**
     * 모든 스팟 조회 (지도 전체 마커 조회용)
     *
     * @return 스팟 목록
     */
    List<Spot> findAll();

    /**
     * 모든 스팟과 최신 사진 위치 정보를 한 번의 쿼리로 조회 (N+1 문제 해결)
     * 각 스팟의 가장 최근에 생성된 SPOT 타입 사진의 위도/경도 정보를 함께 조회
     *
     * @param type 사진 타입 (SPOT)
     * @return 스팟 마커 정보 목록 (spotId, latitude, longitude)
     */
    @Query("SELECT new com.gemmap.gemmap.spot.application.dto.response.SpotMakerInfo(s.id, sp.latitude, sp.longitude) " +
           "FROM Spot s " +
           "INNER JOIN SpotPhoto sp ON sp.spot.id = s.id " +
           "WHERE sp.type = :type " +
           "AND sp.createdAt = (SELECT MAX(sp2.createdAt) FROM SpotPhoto sp2 WHERE sp2.spot.id = s.id AND sp2.type = :type)")
    List<SpotMakerInfo> findAllSpotsWithLatestPhotoLocation(@Param("type") ESpotPhotoType type);

}
