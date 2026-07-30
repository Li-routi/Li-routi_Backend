package com.lirouti.domain.home.converter;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.home.dto.response.HomeResDTO;
import com.lirouti.domain.member.entity.Member;

import java.util.List;

public class HomeConverter {

    public static HomeResDTO.UserInfo toUserInfo(Member member) {
        return HomeResDTO.UserInfo.builder()
                .memberId(member.getId())
                .nickname(member.getNickname())
                .build();
    }

    public static HomeResDTO.MyRoutines toMyRoutines(List<ChallengeResDTO.MySummary> challenges) {
        List<ChallengeResDTO.MySummary> safeChallenges = (challenges != null) ? challenges : List.of();
        return HomeResDTO.MyRoutines.builder()
                .challenges(safeChallenges)
                .isEmpty(safeChallenges.isEmpty())
                .build();
    }

    public static HomeResDTO.GroupRoutines toGroupRoutines(List<GroupResDTO.TodayRoutine> routines) {
        List<GroupResDTO.TodayRoutine> safeRoutines = (routines != null) ? routines : List.of();
        return HomeResDTO.GroupRoutines.builder()
                .routines(safeRoutines)
                .isEmpty(safeRoutines.isEmpty())
                .build();
    }

    public static HomeResDTO.MainSummary toMainSummary(
            HomeResDTO.UserInfo userInfo,
            HomeResDTO.MyRoutines myRoutines,
            HomeResDTO.GroupRoutines groupRoutines
    ) {
        return HomeResDTO.MainSummary.builder()
                .userInfo(userInfo)
                .myRoutines(myRoutines)
                .groupRoutines(groupRoutines)
                .build();
    }
}
