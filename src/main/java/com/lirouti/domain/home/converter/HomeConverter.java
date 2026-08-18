package com.lirouti.domain.home.converter;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.home.dto.response.HomeResDTO;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;

import java.util.List;

public class HomeConverter {

    public static HomeResDTO.UserInfo toUserInfo(
            Member member,
            HomeResDTO.RepresentativeAchievement representativeAchievement
    ) {
        return HomeResDTO.UserInfo.builder()
                .memberId(member.getId())
                .nickname(member.getNickname())
                .representativeAchievement(representativeAchievement)
                .build();
    }

    public static HomeResDTO.MyRoutines toMyRoutines(List<RoutineResDTO.Routine> routines) {
        List<RoutineResDTO.Routine> safeRoutines = (routines != null) ? routines : List.of();
        return HomeResDTO.MyRoutines.builder()
                .routines(safeRoutines)
                .isEmpty(safeRoutines.isEmpty())
                .build();
    }

    public static HomeResDTO.GroupRoutines toGroupRoutines(List<GroupResDTO.TodayRoutine> groupRoutines) {
        List<GroupResDTO.TodayRoutine> safeGroupRoutines = (groupRoutines != null) ? groupRoutines : List.of();
        return HomeResDTO.GroupRoutines.builder()
                .routines(safeGroupRoutines)
                .isEmpty(safeGroupRoutines.isEmpty())
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
