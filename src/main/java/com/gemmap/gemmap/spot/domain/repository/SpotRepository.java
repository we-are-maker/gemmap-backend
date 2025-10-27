package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.spot.domain.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotRepository extends JpaRepository<Spot, Long> {
}
