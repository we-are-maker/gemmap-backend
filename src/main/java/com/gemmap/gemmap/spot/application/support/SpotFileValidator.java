package com.gemmap.gemmap.spot.application.support;

import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

@Component
public class SpotFileValidator {

    private static final Set<String> ALLOWED = Set.of(
        "image/jpeg", "image/heic", "image/tiff", "image/dng", "image/webp", "image/png"
    );

    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "파일이 비어있습니다.");
        }

        final String ct = Optional.ofNullable(file.getContentType()).orElse("");
        final boolean ctAllowed = ALLOWED.contains(ct);

        // 가정/추측함: 일부 클라이언트가 octet-stream으로 전송 → 시그니처 통과 시 허용
        final boolean allowOctetStreamBySignature = true;

        if (!ctAllowed && !(allowOctetStreamBySignature && "application/octet-stream".equals(ct))) {
            throw new CommonException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        byte[] head = readHead(file, 12);
        if (!matchesAny(head, ct)) {
            throw new CommonException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private byte[] readHead(MultipartFile f, int n) {
        try (InputStream in = f.getInputStream()) {
            byte[] buf = new byte[n];
            int r = in.read(buf, 0, n);
            return r > 0 ? Arrays.copyOf(buf, r) : new byte[0];
        } catch (IOException e) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "파일을 읽을 수 없습니다.");
        }
    }

    private boolean matchesAny(byte[] h, String ct) {
        return isJpeg(h, ct) || isPng(h, ct) || isWebp(h, ct) || isTiffOrDng(h, ct) || isHeic(h, ct);
    }

    private boolean isJpeg(byte[] h, String ct) {
        return (is(ct, "image/jpeg") || is(ct, "application/octet-stream"))
            && h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] h, String ct) {
        byte[] sig = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return (is(ct, "image/png") || is(ct, "application/octet-stream"))
            && startsWith(h, sig);
    }

    private boolean isWebp(byte[] h, String ct) {
        return (is(ct, "image/webp") || is(ct, "application/octet-stream"))
            && h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
            && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    private boolean isTiffOrDng(byte[] h, String ct) {
        boolean ctOk = is(ct, "image/tiff") || is(ct, "image/dng") || is(ct, "application/octet-stream");
        boolean little = h.length >= 4 && h[0] == 'I' && h[1] == 'I' && h[2] == '*' && h[3] == 0;
        boolean big = h.length >= 4 && h[0] == 'M' && h[1] == 'M' && h[2] == 0 && h[3] == '*';
        return ctOk && (little || big);
    }

    private boolean isHeic(byte[] h, String ct) {
        if (!(is(ct, "image/heic") || is(ct, "application/octet-stream"))) return false;
        if (h.length < 12) return false;
        boolean ftyp = h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        if (!ftyp) return false;
        String brand = new String(Arrays.copyOfRange(h, 8, 12), StandardCharsets.US_ASCII);
        return Set.of("heic", "heif", "hevc", "hevx", "mif1", "msf1").contains(brand);
    }

    private boolean startsWith(byte[] h, byte[] sig) {
        if (h.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if (h[i] != sig[i]) return false;
        }
        return true;
    }

    private boolean is(String a, String b) {
        return a != null && a.equals(b);
    }
}
