package com.gemmap.gemmap.bookmark.domain.repository;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.bookmark.domain.entity.SpotBookmark;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpotBookmarkRepository extends JpaRepository<SpotBookmark, Long> {

    /** 중복 찜 방지 **/
    boolean existsByUserAndSpot(User user, Spot spot);

    /** 찜 취소 / 끌림지수 조회 **/
    Optional<SpotBookmark> findByUserIdAndSpotId(Long userId, Long spotId);

    /** 찜한 젬 목록 (최신순) **/
    List<SpotBookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 찜한 젬 개수 **/
    Integer countByUserId(Long userId);

    /** 스팟의 찜 받은 개수 **/
    Integer countBySpotId(Long spotId);
}
