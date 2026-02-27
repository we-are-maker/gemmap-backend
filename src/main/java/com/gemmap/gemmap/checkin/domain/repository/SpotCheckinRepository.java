package com.gemmap.gemmap.checkin.domain.repository;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.checkin.domain.entity.SpotCheckin;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpotCheckinRepository extends JpaRepository<SpotCheckin, Long> {

    /** 중복 체크인 방지 **/
    boolean existsByUserAndSpot(User user, Spot spot);

    /** 체크인 취소 / 추천지수 조회 **/
    Optional<SpotCheckin> findByUserIdAndSpotId(Long userId, Long spotId);

    /** 체크인한 젬 개수 **/
    Integer countByUserId(Long userId);

    /** 스팟의 체크인 개수 **/
    Integer countBySpotId(Long spotId);

    /** 체크인 젬 목록 (최신순) **/
    @EntityGraph(attributePaths = {"spot", "photo"})
    List<SpotCheckin> findByUserIdOrderByCreatedAtDesc(Long userId);
}
