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
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false, unique = true)
    private Long id;

    @Column(name = "social_id", unique = true)
    private String socialId;

    @Column(name = "provider", nullable = false)
    @Enumerated(EnumType.STRING)
    private EProvider eProvider;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private ERole role;

    @Builder.Default
    @Column(name = "create_date", nullable = false)
    private LocalDate createDate = LocalDate.now();

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

    /* User Info */

    @Column(name = "email")
    private String email;

    @Column(name = "nickname", unique = true)
    private String nickname;

    @Column(name = "profile_image")
    private String profileImage;

    @Builder
    public User(String socialId, EProvider eProvider, ERole role, String email,
                String nickname, String profileImage) {
        this.socialId = socialId;
        this.eProvider = eProvider;
        this.role = role;
        this.createDate = LocalDate.now();
        this.isLogin = false;
        this.isDeleted = false;
        this.email = email;
        this.nickname = nickname;
        this.profileImage = profileImage != null ? profileImage : Constant.DEFAULT_PROFILE_IMAGE;
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

    public void updateKakaoUserInfo(String nickname, String profileImage) {
        if (nickname != null) this.nickname = nickname;
        if (profileImage != null) this.profileImage = profileImage;
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
        this.deleteDate = LocalDate.now();
        this.refreshToken = null;
        this.isLogin = false;
    }

    public void recoverUser() {
        this.isDeleted = false;
        this.deleteDate = null;
    }
}