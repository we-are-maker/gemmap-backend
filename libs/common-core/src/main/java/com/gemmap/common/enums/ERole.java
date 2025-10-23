package com.gemmap.common.enums;

import lombok.Getter;

/**
 * 사용자 권한 열거형
 */
@Getter
public enum ERole {

    USER("USER", "ROLE_USER"),
    ADMIN("ADMIN", "ROLE_ADMIN"),
    GUEST("GUEST", "ROLE_GUEST");

    private final String name;
    private final String securityName;

    ERole(String name, String securityName) {
        this.name = name;
        this.securityName = securityName;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public String toSecurityString() {
        return this.securityName;
    }
}
