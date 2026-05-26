package com.gemmap.gemmap.auth.domain.repository;

import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.auth.domain.entity.User;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 사용자 데이터 접근 계층
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByRefreshToken(String refreshToken);

    Boolean existsByNickname(String nickname);

    @Query("SELECT u FROM User u WHERE u.id = :userId AND u.isLogin = :isLogin AND u.refreshToken IS NOT NULL AND u.isDeleted = false")
    Optional<User> findByIdAndIsLoginAndRefreshTokenIsNotNull(Long userId, boolean isLogin);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.refreshToken = :refreshToken, u.isLogin = :status WHERE u.id = :id AND u.isDeleted = false")
    void updateRefreshTokenAndLoginStatus(Long id, String refreshToken, boolean status);

    @Query("SELECT u FROM User u WHERE u.socialId = :socialId AND u.eProvider = :provider AND u.isDeleted = false")
    Optional<User> findBySocialIdAndProvider(String socialId, EProvider provider);

    @Query("SELECT u FROM User u WHERE u.socialId = :socialId AND u.eProvider = :provider AND u.isDeleted = true")
    Optional<User> findSoftDeletedBySocialIdAndProvider(String socialId, EProvider provider);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.isDeleted = false")
    Optional<User> findByEmail(String email);

    @Transactional
    @Modifying
    @Query("DELETE FROM User u WHERE u.isDeleted = true and u.deleteDate < :cutoffDate")
    Long deleteUsersByDeleteDate(LocalDate cutoffDate);
}