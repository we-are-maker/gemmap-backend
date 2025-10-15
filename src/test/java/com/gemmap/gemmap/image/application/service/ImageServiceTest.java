package com.gemmap.gemmap.image.application.service;

import com.gemmap.gemmap.image.domain.entity.Image;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.image.domain.repository.ImageRepository;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.image.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.image.infrastructure.objectstorage.S3UrlGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @InjectMocks
    private ImageService imageService;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private S3UrlGenerator s3UrlGenerator;

    @BeforeEach
    void setUp() {
        // Mockito.lenient()를 사용하여 불필요한 stubbing 경고를 방지
        lenient().when(s3Properties.getBasePath()).thenReturn("spots/");
        lenient().when(s3Properties.getBucket()).thenReturn("test-bucket");
    }

    @Test
    @DisplayName("이미지 여러 개 업로드 성공")
    void uploadImages_success_shouldUploadMultipleFiles() {
        // given
        MockMultipartFile file1 = new MockMultipartFile("files", "test1.jpg", "image/jpeg", "test data 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "test2.png", "image/png", "test data 2".getBytes());
        List<MultipartFile> files = List.of(file1, file2);

        when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
            Image savedImage = invocation.getArgument(0);
            // Simulate saving by setting a mock ID using reflection
            try {
                java.lang.reflect.Field idField = Image.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(savedImage, new java.util.Random().nextLong());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return savedImage;
        });
        when(s3UrlGenerator.generateUrl(anyString())).thenReturn("http://mock.url/test.jpg");

        // when
        List<Image> result = imageService.uploadImages(files);

        // then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(objectStorageService, times(2)).upload(anyString(), anyString(), any(MockMultipartFile.class));
        verify(imageRepository, times(2)).save(any(Image.class));
    }

    @Test
    @DisplayName("DB 저장 실패 시 S3 객체 삭제 (보상 트랜잭션) - 단일 파일")
    void uploadImages_dbFail_shouldDeleteS3Object() {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        List<MultipartFile> files = List.of(file);
        when(imageRepository.save(any(Image.class))).thenThrow(new RuntimeException("DB Error"));

        // when & then
        assertThrows(CommonException.class, () -> imageService.uploadImages(files));
        verify(objectStorageService, times(1)).upload(anyString(), anyString(), eq(file));
        verify(objectStorageService, times(1)).delete(anyString(), anyString());
    }

    @Test
    @DisplayName("잘못된 MIME 타입으로 업로드 시 예외 발생 - 단일 파일")
    void uploadImages_invalidMimeType_shouldThrowException() {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
        List<MultipartFile> files = List.of(file);

        // when & then
        assertThrows(CommonException.class, () -> imageService.uploadImages(files));
        verify(objectStorageService, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("업로드 파일 개수 제한(5개) 초과 시 예외 발생")
    void uploadImages_exceedsLimit_shouldThrowException() {
        // given
        List<MultipartFile> files = IntStream.range(0, 6)
                .mapToObj(i -> new MockMultipartFile("files", "test" + i + ".jpg", "image/jpeg", ("test data " + i).getBytes()))
                .collect(Collectors.toList());

        // when & then
        assertThrows(CommonException.class, () -> imageService.uploadImages(files));
        verify(objectStorageService, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("이미지 삭제 성공")
    void deleteImage_success() {
        // given
        String fileUrl = "http://mock.url/test-bucket/spots/2024/10/uuid.jpg";
        String key = "spots/2024/10/uuid.jpg";
        Image image = Image.builder().fileUrl(fileUrl).build();
        try {
            java.lang.reflect.Field idField = Image.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(image, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(s3UrlGenerator.extractKeyFromUrl(fileUrl)).thenReturn(key);
        when(imageRepository.findByFileUrl(fileUrl)).thenReturn(java.util.Optional.of(image));

        // when
        imageService.deleteImage(fileUrl);

        // then
        verify(objectStorageService, times(1)).delete("test-bucket", key);
        verify(imageRepository, times(1)).findByFileUrl(fileUrl);
        verify(imageRepository, times(1)).delete(image);
    }

    @Test
    @DisplayName("DB에 없는 이미지 삭제 시 S3 객체만 삭제")
    void deleteImage_notFoundInDB_shouldOnlyDeleteFromS3() {
        // given
        String fileUrl = "http://mock.url/test-bucket/spots/2024/10/uuid.jpg";
        String key = "spots/2024/10/uuid.jpg";

        when(s3UrlGenerator.extractKeyFromUrl(fileUrl)).thenReturn(key);
        when(imageRepository.findByFileUrl(fileUrl)).thenReturn(java.util.Optional.empty());

        // when
        imageService.deleteImage(fileUrl);

        // then
        verify(objectStorageService, times(1)).delete("test-bucket", key);
        verify(imageRepository, times(1)).findByFileUrl(fileUrl);
        verify(imageRepository, never()).delete(any(Image.class));
    }
}
