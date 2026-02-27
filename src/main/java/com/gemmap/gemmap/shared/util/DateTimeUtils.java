package com.gemmap.gemmap.shared.util;

import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 날짜/시간 처리 유틸리티
 *
 * 촬영 시각(takenAt) 문자열을 KST 기준 LocalDateTime으로 파싱한다.
 * ISO 8601 형식의 오프셋 포함/미포함 문자열을 모두 지원한다.
 */
public final class DateTimeUtils {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private DateTimeUtils() {}

    /**
     * 촬영 시각 문자열을 LocalDateTime(KST)으로 파싱
     *
     * 지원 형식 (우선순위 순):
     * - 오프셋 포함 (권장)</b>: "2025-11-01T14:51:24+09:00" → KST 변환하여 저장
     * - UTC (Z suffix)</b>: "2025-11-01T05:51:24Z" → KST 변환하여 저장 (+9시간)
     * - 오프셋 없음 (Fallback)</b>: "2025-11-01T14:51:24" → KST로 가정하여 저장
     *
     * @param takenAt ISO 8601 형식의 촬영 시각 문자열 (null 허용)
     * @return KST 기준 LocalDateTime, 입력이 null/blank이면 null
     * @throws CommonException takenAt 형식이 올바르지 않은 경우
     */
    public static LocalDateTime parseTakenAt(String takenAt) {
        if (takenAt == null || takenAt.isBlank()) return null;

        try {
            // 1. 타임존/오프셋 정보가 포함된 경우: OffsetDateTime으로 파싱 후 KST 변환
            if (hasTimezoneInfo(takenAt)) {
                OffsetDateTime odt = OffsetDateTime.parse(takenAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                // 절대 시간(Instant)은 유지하고 표기만 KST로 변환
                return odt.atZoneSameInstant(KST).toLocalDateTime();
            }

            // 2. 타임존 정보가 없는 경우 (Fallback): KST로 가정하여 처리
            // 주의: 해외에서 촬영한 사진의 경우 실제 시간과 다를 수 있음
            return LocalDateTime.parse(takenAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        } catch (DateTimeParseException e) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE,
                    "takenAt 형식이 올바르지 않습니다. ISO 8601 형식을 사용하세요. " +
                            "(권장: 2025-11-01T14:51:24+09:00, 허용: 2025-11-01T14:51:24Z, 2025-11-01T14:51:24)");
        }
    }

    /**
     * 문자열에 타임존/오프셋 정보가 포함되어 있는지 확인
     *
     * @param dateTimeStr ISO 8601 형식의 날짜/시간 문자열
     * @return 타임존 정보 포함 여부
     */
    static boolean hasTimezoneInfo(String dateTimeStr) {
        // Z (UTC), + (양수 오프셋), 또는 T 이후의 - (음수 오프셋) 확인
        // 예: 2025-11-01T14:51:24Z, 2025-11-01T14:51:24+09:00, 2025-11-01T14:51:24-05:00
        if (dateTimeStr.endsWith("Z")) return true;
        if (dateTimeStr.contains("+")) return true;

        // T 이후에 -가 있으면 오프셋 (날짜 부분의 -와 구분)
        int tIndex = dateTimeStr.indexOf('T');
        if (tIndex > 0) {
            String timePart = dateTimeStr.substring(tIndex);
            return timePart.contains("-");
        }
        return false;
    }
}