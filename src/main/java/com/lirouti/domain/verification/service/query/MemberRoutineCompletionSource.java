package com.lirouti.domain.verification.service.query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.routine.service.query.RoutineCompletionSource;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 개인 루틴의 완료 여부를 인증 기록으로 답한다.
 *
 * <p>개인 루틴에는 날짜별 행이 없어 인증 행 자체가 완료 기록이다. 그래서 "그날 인증이
 * 있는가"가 곧 "그날 완료했는가"다. 그룹 루틴과 다른 점이다 — 그쪽은 할당의 status 가
 * 완료를 들고 있다.
 */
@Component
@RequiredArgsConstructor
public class MemberRoutineCompletionSource implements RoutineCompletionSource {

    private final MemberRoutineVerificationRepository verificationRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findCompletedRoutineIds(Collection<Long> routineIds, LocalDate date) {
        if (routineIds.isEmpty()) {
            // 빈 컬렉션으로 IN 절을 만들면 DB에 따라 문법 오류가 난다. 질의 자체를 하지 않는다.
            return Set.of();
        }
        return new HashSet<>(verificationRepository.findVerifiedRoutineIds(routineIds, date));
    }
}
