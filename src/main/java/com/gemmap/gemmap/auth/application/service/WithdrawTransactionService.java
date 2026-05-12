package com.gemmap.gemmap.auth.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 마무리 DB 업데이트 전담 서비스.
 * 외부 API 호출은 AuthService에서 트랜잭션 밖에서 수행 후 이 메서드 호출.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawTransactionService {

    private final UserRepository userRepository;

    @Transactional
    public void finalizeWithdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        user.clearAppleRefreshToken();
        user.withdrawUser();

        log.info("회원 탈퇴 DB 반영 완료 - userId: {}", userId);
    }
}
