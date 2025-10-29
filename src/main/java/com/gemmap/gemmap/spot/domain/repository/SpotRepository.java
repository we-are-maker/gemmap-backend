package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
