package com.gemmap.spot.image.infrastructure.objectstorage;

import com.gemmap.common.exception.ErrorCode;
import com.gemmap.common.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final S3Client s3Client;

    /**
     * 파일을 Object Storage에 업로드합니다.
     *
     * @param bucket 버킷 이름
     * @param key    객체 키 (파일 경로 포함)
     * @param file   업로드할 파일
     */
    public void upload(String bucket, String key, MultipartFile file) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("File uploaded successfully to S3: bucket={}, key={}", bucket, key);

        } catch (IOException e) {
            log.error("Failed to get InputStream from MultipartFile", e);
            throw new CommonException(ErrorCode.S3_OPERATION_FAILED, "파일 스트림을 읽는 중 오류가 발생했습니다.");
        } catch (SdkException e) {
            log.error("Failed to upload file to S3: bucket={}, key={}", bucket, key, e);
            throw new CommonException(ErrorCode.S3_OPERATION_FAILED, "Object Storage에 파일을 업로드하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * Object Storage에서 객체를 삭제합니다.
     *
     * @param bucket 버킷 이름
     * @param key    객체 키
     */
    public void delete(String bucket, String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("File deleted successfully from S3: bucket={}, key={}", bucket, key);

        } catch (SdkException e) {
            log.error("Failed to delete file from S3: bucket={}, key={}", bucket, key, e);
            throw new CommonException(ErrorCode.S3_OPERATION_FAILED, "Object Storage에서 파일을 삭제하는 중 오류가 발생했습니다.");
        }
    }
}
