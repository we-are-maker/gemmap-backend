package com.gemmap.common.security.jwt;

import com.gemmap.common.enums.ERole;

public record JwtUserInfo(Long userId, ERole role) {}
