package com.gemmap.gemmap.auth.application.dto.response;

import com.gemmap.gemmap.shared.common.enums.EGender;

import java.time.LocalDate;

/**
 * 회원가입 화면 prefill 조회 응답 DTO.
 * 카카오: 저장값 / 애플·미동의·기본 이미지: null (클라이언트가 화면 기본값 처리)
 */
public record RegisterProfileResponseDto(
        String nickname,
        LocalDate birthDate,   // 직렬화 시 ISO yyyy-MM-dd
        EGender gender,        // 직렬화 시 "MALE"/"FEMALE"/null
        String profileImage    // 카카오 이미지 URL / 기본 이미지·애플 → null
) {
    public static RegisterProfileResponseDto of(String nickname, LocalDate birthDate,
                                                EGender gender, String profileImage) {
        return new RegisterProfileResponseDto(nickname, birthDate, gender, profileImage);
    }
}
