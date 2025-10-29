package com.gemmap.gemmap.spot.application.dto.response;

import com.gemmap.gemmap.auth.domain.entity.User;
import lombok.Builder;

import java.util.List;

/**
 * 내가 제보한 스팟 목록 응답 DTO
 */
@Builder
public record MySpotsResponse(
        String profileImage,    // 사용자 프로필 이미지
        String nickname,        // 사용자 닉네임
        Integer mySpotCount,    // 제보한 젬 개수 (전체)
        List<SpotSummary> spots // 제보한 젬 목록
) {
    public static MySpotsResponse of(User user, Integer totalCount, List<SpotSummary> spots) {
        return MySpotsResponse.builder()
                .profileImage(user.getProfileImage())
                .nickname(user.getNickname())
                .mySpotCount(totalCount)
                .spots(spots)
                .build();
    }
}
