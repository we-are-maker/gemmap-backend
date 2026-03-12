package com.gemmap.gemmap.auth.application.dto.response;

public record PhotoConsentResponse(
        boolean agreed
) {
    public static PhotoConsentResponse of(boolean agreed) {
        return new PhotoConsentResponse(agreed);
    }
}
