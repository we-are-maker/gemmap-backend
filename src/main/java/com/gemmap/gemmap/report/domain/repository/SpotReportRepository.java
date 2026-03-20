package com.gemmap.gemmap.report.domain.repository;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.report.domain.entity.SpotReport;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotReportRepository extends JpaRepository<SpotReport, Long> {

    boolean existsByUserAndSpot(User user, Spot spot);
}
