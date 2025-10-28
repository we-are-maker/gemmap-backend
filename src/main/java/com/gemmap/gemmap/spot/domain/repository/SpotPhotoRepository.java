package com.gemmap.gemmap.spot.domain.repository;

import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {
    List<SpotPhoto> findBySpot(Spot spot);
}
