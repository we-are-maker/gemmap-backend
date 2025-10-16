package com.gemmap.gemmap.image.application.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.gemmap.gemmap.image.domain.entity.Image;
import lombok.experimental.UtilityClass;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.Date;
import java.util.Optional;

@UtilityClass
public class ExifExtractor {

    public static Image.ImageBuilder extract(MultipartFile file, Image.ImageBuilder builder) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            // Extract TakenAt
            extractTakenAt(metadata).ifPresent(builder::takenAt);

            // Extract GPS
            extractGps(metadata).ifPresent(coordinate -> {
                builder.latitude(BigDecimal.valueOf(coordinate.getLatitude()));
                builder.longitude(BigDecimal.valueOf(coordinate.getLongitude()));
            });

            // Extract Camera Info
            extractCameraMake(metadata).ifPresent(builder::cameraMake);
            extractCameraModel(metadata).ifPresent(builder::cameraModel);
            extractFocalLength(metadata).ifPresent(builder::focalLength);
            extractAperture(metadata).ifPresent(builder::aperture);
            extractIso(metadata).ifPresent(builder::iso);
            extractShutterSpeed(metadata).ifPresent(builder::shutterSpeed);

        } catch (Exception e) {
            // Log the error but don't block the upload
        }
        return builder;
    }

    private static Optional<LocalDateTime> extractTakenAt(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class))
                .map(dir -> dir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL))
                .map(Date::toInstant)
                .map(instant -> LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC));
    }

    private static Optional<com.drew.lang.GeoLocation> extractGps(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(GpsDirectory.class))
                .map(GpsDirectory::getGeoLocation);
    }

    private static Optional<String> extractCameraMake(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class))
                .map(dir -> dir.getString(ExifSubIFDDirectory.TAG_MAKE))
                .or(() -> Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class))
                        .map(dir -> dir.getString(ExifIFD0Directory.TAG_MAKE)));
    }

    private static Optional<String> extractCameraModel(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class))
                .map(dir -> dir.getString(ExifSubIFDDirectory.TAG_MODEL))
                .or(() -> Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class))
                        .map(dir -> dir.getString(ExifIFD0Directory.TAG_MODEL)));
    }

    private static Optional<BigDecimal> extractFocalLength(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class))
                .map(dir -> dir.getRational(ExifSubIFDDirectory.TAG_FOCAL_LENGTH))
                .map(rational -> BigDecimal.valueOf(rational.doubleValue()));
    }

    private static Optional<BigDecimal> extractAperture(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class))
                .map(dir -> dir.getRational(ExifSubIFDDirectory.TAG_FNUMBER))
                .map(rational -> BigDecimal.valueOf(rational.doubleValue()));
    }

    private static Optional<Integer> extractIso(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class))
                .map(dir -> dir.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
    }

    private static Optional<String> extractShutterSpeed(Metadata metadata) {
        return Optional.ofNullable(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class))
                .map(dir -> dir.getDescription(ExifSubIFDDirectory.TAG_SHUTTER_SPEED));
    }
}
