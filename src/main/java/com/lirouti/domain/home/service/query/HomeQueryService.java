package com.lirouti.domain.home.service.query;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.domain.home.dto.response.HomeResDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeQueryService {

    private final MemberQueryService memberQueryService;
    private final ChallengeQueryService challengeQueryService;
    private final GroupQueryService groupQueryService;

    /**
     * 홈 화면 대시보드 데이터 통합 조회
     */
    @Transactional(readOnly = true)
    public HomeResDTO.MainSummary getHomeSummary(Long memberId) {
        // 1. 회원 정보 조회
        Member member = memberQueryService.getActiveMember(memberId);

        HomeResDTO.UserInfo userInfo = HomeResDTO.UserInfo.builder()
                .memberId(member.getId())
                .nickname(member.getNickname())
                .build();

        // 2. 오늘의 루틴 목록 조회
        ChallengeResDTO.MyListing myListing = challengeQueryService.getMyChallenges(memberId, null, null);
        List<ChallengeResDTO.MySummary> challenges = myListing.challenges();

        HomeResDTO.MyRoutines myRoutines = HomeResDTO.MyRoutines.builder()
                .challenges(challenges)
                .isEmpty(challenges == null || challenges.isEmpty())
                .build();

        // 3. 그룹 루틴 목록 조회
        GroupResDTO.TodayRoutineList todayGroupRoutines = groupQueryService.getTodayRoutines(memberId);
        List<GroupResDTO.TodayRoutine> groupRoutinesList = todayGroupRoutines.routines();

        HomeResDTO.GroupRoutines groupRoutines = HomeResDTO.GroupRoutines.builder()
                .routines(groupRoutinesList)
                .isEmpty(groupRoutinesList == null || groupRoutinesList.isEmpty())
                .build();

        return HomeResDTO.MainSummary.builder()
                .userInfo(userInfo)
                .myRoutines(myRoutines)
                .groupRoutines(groupRoutines)
                .build();
    }
}
