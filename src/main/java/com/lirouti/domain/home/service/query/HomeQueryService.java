package com.lirouti.domain.home.service.query;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.domain.home.converter.HomeConverter;
import com.lirouti.domain.home.dto.response.HomeResDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.routine.service.query.RoutineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeQueryService {

    private final MemberQueryService memberQueryService;
    private final RoutineQueryService routineQueryService;
    private final GroupQueryService groupQueryService;

    /**
     * 홈 화면 요약 정보를 조회한다.
     * '오늘의 루틴' 탭은 Routine 도메인에서 오늘 반복 요일에 해당하는 목록을 가져온다.
     *
     * @param memberId: 조회를 요청한 ID
     * @return 유저 정보, 오늘의 개인 루틴, 그룹 루틴을 합친 홈 화면 요약
     */
    @Transactional(readOnly = true)
    public HomeResDTO.MainSummary getHomeSummary(Long memberId) {
        Member member = memberQueryService.getActiveMember(memberId);

        HomeResDTO.UserInfo userInfo = HomeConverter.toUserInfo(member);
        HomeResDTO.MyRoutines myRoutines = HomeConverter.toMyRoutines(routineQueryService.getTodayRoutines(memberId));
        HomeResDTO.GroupRoutines groupRoutines = HomeConverter.toGroupRoutines(groupQueryService.getTodayRoutines(memberId).routines());

        return HomeConverter.toMainSummary(userInfo, myRoutines, groupRoutines);
    }
}
