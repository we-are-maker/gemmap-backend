package com.gemmap.gemmap.auth.domain.entity;

import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 사용자 엔티티
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@DynamicUpdate
@SQLDelete(sql = "UPDATE users SET is_deleted = true WHERE id = ?")
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_social_id_provider",
                        columnNames = {"social_id", "provider"}
                )
        }
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false, unique = true)
    private Long id;

    @Column(name = "social_id")
    private String socialId;

    @Column(name = "provider", nullable = false)
    @Enumerated(EnumType.STRING)
    private EProvider eProvider;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private ERole role;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Builder.Default
    @Column(name = "create_date", nullable = false)
    private LocalDate createDate = LocalDate.now(KST);

    @Column(name = "refresh_token")
    private String refreshToken;

    @Builder.Default
    @Column(name = "is_login", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isLogin = false;

    @Builder.Default
    @Column(name = "is_deleted", columnDefinition = "TINYINT(1)", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "delete_date")
    private LocalDate deleteDate;

    /**
     * 사진 정보 활용 동의 시각.
     * non-null: 동의 상태 (마지막 동의 시점)
     * null    : 미동의 또는 철회 완료
     * 단일 필드 정책 — 별도 철회 시각 컬럼 두지 않음. 판정은 {@link #hasPhotoConsentAgreed()}.
     */
    @Column(name = "photo_consent_agreed_at")
    private LocalDateTime photoConsentAgreedAt;

    @Column(name = "apple_refresh_token", length = 500)
    private String appleRefreshToken;

    /* User Info */

    @Column(name = "email")
    private String email;

    @Column(name = "name")
    private String name;

    @Column(name = "nickname", unique = true)
    private String nickname;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "gender")
    private String gender;

    @Column(name = "age_range")
    private String ageRange;

    @Column(name = "birthday")
    private String birthday;

    @Column(name = "birthyear")
    private String birthyear;

    @Builder
    public User(String socialId, EProvider eProvider, ERole role, String email, String name,
                String nickname, String profileImage, String gender, String ageRange,
                String birthday, String birthyear) {
        this.socialId = socialId;
        this.eProvider = eProvider;
        this.role = role;
        this.createDate = LocalDate.now(KST);
        this.isLogin = false;
        this.isDeleted = false;
        this.email = email;
        this.name = name;
        this.nickname = nickname;
        this.profileImage = profileImage != null ? profileImage : Constant.DEFAULT_PROFILE_IMAGE;
        this.gender = gender;
        this.ageRange = ageRange;
        this.birthday = birthday;
        this.birthyear = birthyear;
    }

    public void updateNickname(String nickname) {
        if(nickname != null && !nickname.equals(this.nickname)){
            this.nickname = nickname;
        }
    }

    public void updateRole(ERole role) {
        this.role = role;
    }

    public boolean isLogin() {
        return this.isLogin != null && this.isLogin;
    }

    public void updateLoginStatus(Boolean isLogin) {
        this.isLogin = isLogin;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public void updateKakaoUserInfo(String name, String nickname, String profileImage,
                                    String gender, String ageRange, String birthday, String birthyear) {
        if (name != null) this.name = name;
        if (nickname != null) this.nickname = nickname;
        if (profileImage != null) this.profileImage = profileImage;
        if (gender != null) this.gender = gender;
        if (ageRange != null) this.ageRange = ageRange;
        if (birthday != null) this.birthday = birthday;
        if (birthyear != null) this.birthyear = birthyear;
    }

    /**
     * 로그아웃 처리
     * - 로그인 상태를 false로 변경
     * - refresh token 삭제
     */
    public void logout() {
        this.isLogin = false;
        this.refreshToken = null;
    }

    public void withdrawUser() {
        this.isDeleted = true;
        this.deleteDate = LocalDate.now(KST);
        this.refreshToken = null;
        this.isLogin = false;
    }

    public void updateAppleRefreshToken(String encryptedRefreshToken) {
        this.appleRefreshToken = encryptedRefreshToken;
    }

    public void clearAppleRefreshToken() {
        this.appleRefreshToken = null;
    }

    public void recoverUser() {
        this.isDeleted = false;
        this.deleteDate = null;
    }

    /**
     * 유예 기간(30일) 경과 후 재가입 시 권한·임의 프로필 초기화.
     * 소셜 리니어블 필드(email, name, gender, ageRange, birthday, birthyear)는 별도 갱신.
     * 연관 데이터(spot/bookmark/checkin/photo/report)는 본 스코프 외.
     */
    public void resetForRejoin() {
        this.role = ERole.GUEST;
        this.nickname = null;
        this.profileImage = Constant.DEFAULT_PROFILE_IMAGE;
        this.photoConsentAgreedAt = null;
    }

    /**
     * 소셜 제공자에서 받은 최신 email로 갱신.
     * 기존 updateKakaoUserInfo가 email을 다루지 않아 별도 메서드로 분리.
     */
    public void updateEmail(String email) {
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
    }

    /**
     * 탈퇴 유예 기간 만료 여부.
     * deleteDate가 null인 비정상 상태는 유예 경과로 간주(안전한 fallback).
     */
    public boolean isGracePeriodExpired(int gracePeriodDays) {
        if (this.deleteDate == null) {
            return true;
        }
        LocalDate today = LocalDate.now(KST);
        return today.isAfter(this.deleteDate.plusDays(gracePeriodDays));
    }

    public void agreeToPhotoConsent() {
        this.photoConsentAgreedAt = LocalDateTime.now(KST);
    }

    /**
     * 사진 정보 활용 동의 철회.
     * 단일 필드 정책에 따라 동의 시각을 null 로 초기화한다.
     * 호출부(SpotService/CheckinService) 의 {@link #hasPhotoConsentAgreed()} 검증식은 무변경 —
     * 철회 사용자는 photoConsentAgreedAt == null 이 되어 자동 미동의 처리된다.
     */
    public void revokePhotoConsent() {
        this.photoConsentAgreedAt = null;
    }

    public boolean hasPhotoConsentAgreed() {
        return this.photoConsentAgreedAt != null;
    }
}
