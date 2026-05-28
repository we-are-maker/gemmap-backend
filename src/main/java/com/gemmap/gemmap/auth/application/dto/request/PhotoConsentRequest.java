package com.gemmap.gemmap.auth.application.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 사진 정보 활용 동의/철회 요청 Body DTO.
 * agreed=true  → 동의 처리 (멱등: 이미 동의면 시각 보존)
 * agreed=false → 철회 처리 (멱등: 이미 미동의/철회면 변경 없음)
 *
 * Boolean 래퍼 사용 — 원시 boolean 은 JSON null 을 false 로 역직렬화하여
 * @NotNull 검증이 무효화될 수 있다. (01_구현_설계.md §2-4 참조)
 */
public record PhotoConsentRequest(
        @NotNull(message = "agreed 필드는 필수입니다.")
        Boolean agreed
) {
}
