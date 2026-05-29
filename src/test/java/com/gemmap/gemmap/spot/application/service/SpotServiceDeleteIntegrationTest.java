package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SpotService.delete의 afterCommit 동작에 대한 실제 commit 기반 통합 검증.
 *
 * 단위 테스트(SpotServiceTest.DeleteTests)는 동기화 등록·콜백 본문까지만 검증한다.
 * 본 통합 테스트는 Spring 트랜잭션 매니저가 실제 commit 경로에서 afterCommit을
 * 호출하는지를 확인해 hotfix 핵심 보강(S3 삭제 afterCommit 이동)의 회귀를 막는다.
 *
 * SpotPhoto의 location 필드(POINT SRID 4326)는 H2에서 ORM persist가 불안하므로
 * SpotPhotoRepository를 @MockBean으로 격리하고 findFileUrlsBySpot만 스텁한다.
 * User/Spot은 실 DB(H2)에 persist.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpotServiceDeleteIntegrationTest {

    @Autowired private SpotService spotService;
    @Autowired private UserRepository userRepository;
    @Autowired private SpotRepository spotRepository;
    @Autowired private S3Properties s3Properties;                  // real bean (test-bucket 사용)
    @MockBean private SpotPhotoRepository spotPhotoRepository;     // H2 spatial 우회
    @MockBean private ObjectStorageService objectStorageService;   // S3 호출 격리

    private static final String S3_KEY = "dev/spots/test/key.jpg";

    @Test
    @DisplayName("실제 commit 후 afterCommit 콜백이 발동되어 S3 객체 삭제가 호출된다")
    void delete_realCommit_invokesS3CleanupAfterCommit() {
        // Given: User + Spot fixture 영속 (SpotPhoto는 mock 스텁으로 대체)
        User user = userRepository.save(buildTestUser());
        Spot spot = spotRepository.save(buildTestSpot(user));

        given(spotPhotoRepository.findFileUrlsBySpot(spot.getId()))
            .willReturn(List.of(S3_KEY));

        TestTransaction.flagForCommit();
        TestTransaction.end();   // setUp 트랜잭션 종료 (commit)

        // When: 새 트랜잭션에서 delete 호출 + commit
        TestTransaction.start();
        spotService.delete(user.getId(), spot.getId());
        TestTransaction.flagForCommit();
        TestTransaction.end();   // delete 트랜잭션 commit → afterCommit 발동

        // Then: afterCommit 콜백이 실제로 호출되어 S3 mock 호출 발생
        verify(objectStorageService).delete(eq(s3Properties.getBucket()), eq(S3_KEY));
    }

    private User buildTestUser() {
        return User.builder()
            .socialId("test-social-id")
            .eProvider(EProvider.KAKAO)
            .role(ERole.USER)
            .email("test@example.com")
            .name("테스트유저")
            .nickname("tester")
            .build();
    }

    private Spot buildTestSpot(User user) {
        // Spot은 spatial 필드 없음 (sido/sigungu/fullAddress/shortAddress/alias만 NOT NULL)
        return Spot.builder()
            .user(user)
            .sido("서울특별시")
            .sigungu("강남구")
            .fullAddress("대한민국 서울특별시 강남구 테스트로 1")
            .shortAddress("서울특별시 강남구")
            .alias("테스트스팟")
            .build();
    }
}
