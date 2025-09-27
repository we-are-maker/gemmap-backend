package com.gemmap.gemmap.auth.infrastructure.jwt;

import com.gemmap.gemmap.shared.common.enums.ERole;

public record JwtUserInfo(Long userId, ERole role) {}