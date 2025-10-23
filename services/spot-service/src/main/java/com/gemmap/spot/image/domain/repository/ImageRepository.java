package com.gemmap.spot.image.domain.repository;

import com.gemmap.spot.image.domain.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {
    Optional<Image> findByFileUrl(String fileUrl);
}
