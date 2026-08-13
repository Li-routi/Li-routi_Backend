package com.lirouti.domain.activity.service.command;

import com.lirouti.domain.activity.repository.MemberActivityDayRepository;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 활동일을 남기는 쪽.
 *
 * <p><b>인증이 저장되는 트랜잭션 안에서 부른다.</b> 나누면 인증은 남았는데 활동일이 없는
 * 날이 생기고, 그 하루는 어떤 조건에도 세어지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MemberActivityDayCommandService {

    private final MemberActivityDayRepository memberActivityDayRepository;
    private final MemberRoutineRepository memberRoutineRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;

    /**
     * 그룹·챌린지 인증이 남기는 활동일.
     *
     * <p><b>{@code all_completed} 를 건드리지 않는다.</b> 이 값은 개인 루틴만 보는 것이라
     * 여기서 0 을 쓰지만, 이미 1 인 날을 덮어 내리지는 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long memberId, LocalDate activityDate) {
        memberActivityDayRepository.record(memberId, activityDate, false);
    }

    /**
     * 개인 루틴 인증이 남기는 활동일. <b>그날 예정된 것을 전부 했는지도 함께 판정한다.</b>
     *
     * <p><b>판정은 지금 해야 한다.</b> 개인 루틴은 과거에 무엇이 예정돼 있었는지 복원할 수
     * 없다 — 루틴을 수정·삭제하면 요일 행이 물리 삭제되고 삭제 시각도 남지 않으며, 애초에
     * 날짜별 행이 없다. 하루가 지난 뒤 배치로 소급하면 그 사이 루틴을 하나 지운 사람은
     * 어제의 판정이 바뀐다. <b>인증이 들어온 이 순간이 예정 목록을 아는 유일한 시점이다.</b>
     *
     * <p>예정이 0 개인 날은 완수로 보지 않는다. 그렇게 두면 루틴을 전부 지워 놓고 연속을
     * 채울 수 있고, 조건이 묻는 "꾸준히 했다" 와 뜻이 반대가 된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordWithCompletion(Long memberId, LocalDate activityDate) {
        List<MemberRoutine> scheduled =
                memberRoutineRepository.findTodayActiveByMemberId(memberId, activityDate.getDayOfWeek());

        boolean allCompleted = !scheduled.isEmpty() && isAllVerified(scheduled, activityDate);
        memberActivityDayRepository.record(memberId, activityDate, allCompleted);
    }

    private boolean isAllVerified(List<MemberRoutine> scheduled, LocalDate activityDate) {
        List<Long> routineIds = scheduled.stream().map(MemberRoutine::getId).toList();
        Set<Long> verified = Set.copyOf(
                memberRoutineVerificationRepository.findVerifiedRoutineIds(routineIds, activityDate));
        return verified.containsAll(routineIds);
    }
}
