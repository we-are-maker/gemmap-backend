package com.gemmap.gemmap.auth.domain.entity;

import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.enums.EGender;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link User} 도메인 메서드 단위 테스트 (worklog/25_registration_profile_prefill).
 * toBirthDate, EGender.fromKakao, updateBirthDate/updateGender null=keep 검증.
 */
class UserTest {

    private User buildUser(EGender gender, LocalDate birthDate) {
        return User.builder()
                .socialId("test-social-id")
                .eProvider(EProvider.KAKAO)
                .role(ERole.GUEST)
                .email("test@example.com")
                .name("테스트")
                .nickname("testnick")
                .profileImage(null)
                .gender(gender)
                .ageRange("20-29")
                .birthDate(birthDate)
                .build();
    }

    @Nested
    @DisplayName("User.toBirthDate — birthday(MMDD) + birthyear(YYYY) → LocalDate 변환")
    class ToBirthDate {

        @Test
        @DisplayName("정상: birthday=0115, birthyear=2000 → 2000-01-15")
        void normalConversion() {
            assertThat(User.toBirthDate("0115", "2000")).isEqualTo(LocalDate.of(2000, 1, 15));
        }

        @Test
        @DisplayName("birthday null → null 반환")
        void birthdayNull() {
            assertThat(User.toBirthDate(null, "2000")).isNull();
        }

        @Test
        @DisplayName("birthyear null → null 반환")
        void birthyearNull() {
            assertThat(User.toBirthDate("0115", null)).isNull();
        }

        @Test
        @DisplayName("birthday 비정상 포맷(숫자 아님) → null 반환")
        void birthdayInvalidFormat() {
            assertThat(User.toBirthDate("AB15", "2000")).isNull();
        }

        @Test
        @DisplayName("birthyear 비정상 포맷(숫자 아님) → null 반환")
        void birthyearInvalidFormat() {
            assertThat(User.toBirthDate("0115", "20X0")).isNull();
        }

        @Test
        @DisplayName("평년(2023) 0229 불가 조합 → null 반환")
        void nonLeapYear0229() {
            assertThat(User.toBirthDate("0229", "2023")).isNull();
        }

        @Test
        @DisplayName("윤년(2000) 0229 가능 조합 → 2000-02-29")
        void leapYear0229() {
            assertThat(User.toBirthDate("0229", "2000")).isEqualTo(LocalDate.of(2000, 2, 29));
        }
    }

    @Nested
    @DisplayName("EGender.fromKakao — 카카오 성별 원시값 정규화")
    class FromKakao {

        @Test
        @DisplayName("null → null")
        void nullInput() {
            assertThat(EGender.fromKakao(null)).isNull();
        }

        @Test
        @DisplayName("\"male\" → MALE")
        void maleLowercase() {
            assertThat(EGender.fromKakao("male")).isEqualTo(EGender.MALE);
        }

        @Test
        @DisplayName("\"female\" → FEMALE")
        void femaleLowercase() {
            assertThat(EGender.fromKakao("female")).isEqualTo(EGender.FEMALE);
        }

        @Test
        @DisplayName("\"Male\" (대소문자 혼재) → MALE")
        void maleMixed() {
            assertThat(EGender.fromKakao("Male")).isEqualTo(EGender.MALE);
        }

        @Test
        @DisplayName("\"unknown\" (예상외 값) → null")
        void unknownValue() {
            assertThat(EGender.fromKakao("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("updateBirthDate — null=keep (register 정책)")
    class UpdateBirthDate {

        @Test
        @DisplayName("null 전달 시 기존 birthDate 유지")
        void nullKeep() {
            LocalDate original = LocalDate.of(2000, 1, 1);
            User user = buildUser(EGender.MALE, original);

            user.updateBirthDate(null);

            assertThat(user.getBirthDate()).isEqualTo(original);
        }

        @Test
        @DisplayName("값 전달 시 birthDate 갱신")
        void valueApplied() {
            User user = buildUser(EGender.MALE, LocalDate.of(2000, 1, 1));
            LocalDate newDate = LocalDate.of(1990, 5, 15);

            user.updateBirthDate(newDate);

            assertThat(user.getBirthDate()).isEqualTo(newDate);
        }
    }

    @Nested
    @DisplayName("updateGender — null=keep (register 정책)")
    class UpdateGender {

        @Test
        @DisplayName("null 전달 시 기존 gender 유지")
        void nullKeep() {
            User user = buildUser(EGender.MALE, null);

            user.updateGender(null);

            assertThat(user.getGender()).isEqualTo(EGender.MALE);
        }

        @Test
        @DisplayName("값 전달 시 gender 갱신")
        void valueApplied() {
            User user = buildUser(EGender.MALE, null);

            user.updateGender(EGender.FEMALE);

            assertThat(user.getGender()).isEqualTo(EGender.FEMALE);
        }
    }

    @Nested
    @DisplayName("resetForRejoin — 유예 경과 재가입 초기화")
    class ResetForRejoin {

        @Test
        @DisplayName("회원가입 프리필에 노출될 프로필성 정보를 초기화")
        void resetProfilePrefillFields() {
            User user = User.builder()
                    .socialId("test-social-id")
                    .eProvider(EProvider.KAKAO)
                    .role(ERole.USER)
                    .email("test@example.com")
                    .name("테스트")
                    .nickname("oldNick")
                    .profileImage("profiles/original.jpg")
                    .gender(EGender.FEMALE)
                    .ageRange("30-39")
                    .birthDate(LocalDate.of(1990, 5, 15))
                    .build();
            user.agreeToPhotoConsent();

            user.resetForRejoin();

            assertThat(user.getRole()).isEqualTo(ERole.GUEST);
            assertThat(user.getNickname()).isNull();
            assertThat(user.getProfileImage()).isEqualTo(Constant.DEFAULT_PROFILE_IMAGE);
            assertThat(user.getGender()).isNull();
            assertThat(user.getAgeRange()).isNull();
            assertThat(user.getBirthDate()).isNull();
            assertThat(user.hasPhotoConsentAgreed()).isFalse();
        }
    }
}
