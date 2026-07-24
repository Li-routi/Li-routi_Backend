package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupQueryService {
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final MemberQueryService memberQueryService;
    private final Clock clock;

    /**
     * 로그인 회원의 오늘 그룹 루틴 할당을 조회한다.
     * 존재하지 않거나 비활성 상태인 회원은 {@link MemberQueryService}의 도메인 예외를 그대로 전파하고,
     * 활성 그룹 구성원 조건을 만족하는 할당이 없으면 빈 목록을 반환한다.
     *
     * @param memberId 인증 객체에서 추출한 회원 ID
     * @return 오늘의 그룹 루틴 할당 목록
     */
    @Transactional(readOnly = true)
    public GroupResDTO.TodayRoutineList getTodayRoutines(Long memberId) {
        Member member = memberQueryService.getActiveMember(memberId);
        LocalDate today = LocalDate.now(clock);

        List<TodayAssignmentProjection> assignments = groupRoutineAssignmentRepository
                .findTodayAssignmentsByMemberId(member.getId(), today);

        log.debug("오늘의 그룹 루틴 조회를 완료했습니다. memberId={}, assignedDate={}, assignmentCount={}",
                member.getId(), today, assignments.size());

        return GroupConverter.toTodayRoutineList(assignments);
    }
}
