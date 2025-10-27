package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {
}
