package com.lirouti.domain.home.service.query;

import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.domain.home.converter.HomeConverter;
import com.lirouti.domain.home.dto.response.HomeResDTO;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.routine.service.query.RoutineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeQueryService {

    private final MemberQueryService memberQueryService;
    private final RoutineQueryService routineQueryService;
    private final GroupQueryService groupQueryService;
    private final AchievementRepository achievementRepository;
    private final MediaService mediaService;

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

        HomeResDTO.RepresentativeAchievement representativeAchievement =
                resolveRepresentativeAchievement(member.getRepresentativeAchievementId());

        HomeResDTO.UserInfo userInfo = HomeConverter.toUserInfo(member, representativeAchievement);
        HomeResDTO.MyRoutines myRoutines = HomeConverter.toMyRoutines(routineQueryService.getTodayRoutines(memberId));
        HomeResDTO.GroupRoutines groupRoutines = HomeConverter.toGroupRoutines(groupQueryService.getTodayRoutines(memberId).routines());

        return HomeConverter.toMainSummary(userInfo, myRoutines, groupRoutines);
    }

    /**
     * 대표 업적을 설정 안 했으면 null. 설정된 업적이 나중에 비활성화되는 등
     * 조회가 안 되는 예외적인 경우도 안전하게 null로 처리한다 - 홈 화면이
     * 이것 때문에 통째로 깨지면 안 된다.
     */
    private HomeResDTO.RepresentativeAchievement resolveRepresentativeAchievement(Long achievementId) {
        if (achievementId == null) {
            return null;
        }
        return achievementRepository.findById(achievementId)
                .map(achievement -> HomeResDTO.RepresentativeAchievement.builder()
                        .achievementId(achievement.getId())
                        .name(achievement.getName())
                        .badgeImageUrl(mediaService.resolveViewUrl(
                                achievement.getBadgeImageKey(), MediaPurpose.ACHIEVEMENT_BADGE))
                        .build())
                .orElse(null);
    }
}
