package com.lirouti.domain.routine.service.query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * 그날 완료된 루틴이 무엇인지 답하는 쪽. 루틴 목록에 "오늘 완료" 표시를 붙이는 데 쓴다.
 *
 * <p><b>인터페이스를 루틴 도메인이 소유하고 구현은 인증 도메인이 한다.</b> 인증은 이미
 * 루틴 엔티티를 참조하므로, 루틴이 인증을 직접 알면 두 도메인이 서로를 참조하게 된다.
 * 방향을 뒤집어 의존이 <b>인증 → 루틴</b> 한 방향으로만 흐르게 한다
 * (챌린지·미디어 사이에 쓴 것과 같은 방식이다).
 *
 * <p>루틴마다 따로 묻지 않고 한 번에 받는 형태인 것도 의도다. 목록 크기만큼 쿼리가 나가면
 * 홈 화면 한 번에 수십 건이 된다.
 */
public interface RoutineCompletionSource {

    /**
     * @param routineIds 확인할 루틴 id. 비어 있으면 빈 집합을 돌려준다
     * @param date       기준 날짜(KST)
     * @return 그날 완료된 루틴 id
     */
    Set<Long> findCompletedRoutineIds(Collection<Long> routineIds, LocalDate date);
}
