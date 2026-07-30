package com.lirouti.domain.home.dto.response;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.GroupRoutine;
import lombok.Builder;

import java.util.List;

/**
 * 홈 화면 진입 시 필요한 데이터를 한 번에 전달한다
 */
public final class HomeResDTO {
    private HomeResDTO() {
    }

    /**
     * 홈 화면 전체 통합 조회 응답
     */
    @Builder
    public record MainSummary(
            UserInfo userInfo,
            MyRoutines myRoutines,
            GroupRoutines groupRoutines
    ) {
    }

    /**
     * 상단 유저 및 캐릭터 정보
     */
    @Builder
    public record UserInfo(
            Long memberId,
            String nickname,
            String greetingMessage,
            String characterImageUrl
    ) {
    }

    /**
     * '오늘의 루틴' 탭 데이터
     */
    @Builder
    public record MyRoutines(
            List<ChallengeResDTO.MySummary> challenges,
            boolean isEmpty
    ) {
    }

    /**
     * '그룹 루틴' 탭 데이터
     */
    @Builder
    public record GroupRoutines(
            List<GroupResDTO.TodayRoutine> routines,
            boolean isEmpty
    ) {
    }
}
