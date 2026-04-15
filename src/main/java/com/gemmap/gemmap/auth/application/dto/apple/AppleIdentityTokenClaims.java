package com.gemmap.gemmap.auth.application.dto.apple;

/**
 * Apple Identity Token에서 파싱된 클레임 정보
 */
public record AppleIdentityTokenClaims(
        String sub
) {
}
