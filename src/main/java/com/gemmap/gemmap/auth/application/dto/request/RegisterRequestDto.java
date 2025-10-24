package com.gemmap.gemmap.auth.application.dto.request;

import org.springframework.web.multipart.MultipartFile;

/**
 * 회원가입 요청 DTO
 * 닉네임과 프로필 이미지는 선택사항
 */
public record RegisterRequestDto(
        String nickname,
        MultipartFile profileImage
) {
}
