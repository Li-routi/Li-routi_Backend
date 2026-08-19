package com.lirouti.domain.achievement.converter;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.AchievementCondition;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.AchievementProgressType;
import com.lirouti.domain.achievement.enums.MemberAchievementStatus;

import java.util.*;
import java.util.stream.Collectors;

public final class AchievementConverter {

    private AchievementConverter() {
    }

    /**
     * 전체 업적 정의와 회원 진행도, 업적별 이미지 URL을 회원용 응답으로 조합한다.
     * 회원이 아직 진행 중이지 않은 업적도 목록에 포함하며, 이미지 key가 없는 업적은
     * URL map에 포함하지 않아 응답에서 {@code null}로 유지한다.
     *
     * <p><b>아직 달성하지 않은 히든 업적은 목록에서 뺀다.</b> 이름을 "숨겨진 업적"으로 가려
     * 자리만 남기지 않는다 — 자리가 보이면 몇 개가 숨어 있는지, 어느 카테고리에 있는지가
     * 드러나고, 그만큼 숨긴 뜻이 사라진다. 달성한 뒤에는 보통 업적처럼 나온다(그래야 받을 수
     * 있다).
     */
    public static AchievementResDTO.Achievements toAchievements(
            List<Achievement> allAchievements,
            Map<Long, MemberAchievement> memberAchievementByAchievementId,
            Map<Long, List<MemberAchievementCondition>> conditionProgressByMemberAchievementId,
            Map<Long, String> badgeImageUrlByAchievementId
    ) {
        List<Achievement> visibleAchievements = allAchievements.stream()
                .filter(a -> !isUndiscoveredHidden(a, memberAchievementByAchievementId.get(a.getId())))
                .toList();

        Map<com.lirouti.domain.achievement.enums.AchievementCategory, List<AchievementResDTO.AchievementItem>> grouped =
                visibleAchievements.stream()
                        .collect(Collectors.groupingBy(
                                Achievement::getCategory,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        a -> toItem(a, memberAchievementByAchievementId.get(a.getId()),
                                                conditionProgressByMemberAchievementId,
                                                badgeImageUrlByAchievementId),
                                        Collectors.toList()
                                )
                        ));

        List<AchievementResDTO.CategoryGroup> categories = grouped.entrySet().stream()
                .map(e -> AchievementResDTO.CategoryGroup.builder()
                        .category(e.getKey())
                        .achievements(byRemainingWork(e.getValue()))
                        .build())
                .toList();

        // 요약도 보이는 것만 센다. 뺀 업적을 분모에 남기면 "8/36" 인데 목록에는 33개만 있어,
        // 사용자가 찾을 수 없는 셋을 찾게 된다.
        AchievementResDTO.Summary summary =
                toSummary(visibleAchievements, memberAchievementByAchievementId);

        return AchievementResDTO.Achievements.builder()
                .summary(summary)
                .categories(categories)
                .build();
    }

    /**
     * <b>할 일이 남은 순서로 세운다.</b>
     *
     * <pre>
     * ACHIEVED     받기 가능   → 맨 위    지금 누르면 보상이 들어온다
     * IN_PROGRESS  진행 중     → 가운데   앞으로 할 것
     * CLAIMED      받기 완료   → 맨 아래  더 할 일이 없다
     * </pre>
     *
     * <p>받기 버튼이 아래에 묻히면 사용자가 지금 할 수 있는 일을 못 찾고, 다 끝난 업적이 위에
     * 쌓이면 목록을 스크롤할수록 할 일에서 멀어진다.
     *
     * <p>정렬은 <b>안정 정렬</b>이라 같은 칸 안에서는 원래 순서(sort_order)를 그대로 지킨다 —
     * 여기서 다시 흔들면 목록이 조회할 때마다 달라 보인다.
     *
     * <p>카테고리 <b>안에서만</b> 세운다. 응답이 카테고리별로 묶여 나가는 구조라 전체 목록의
     * 맨 위·맨 아래라는 자리가 없다. 앱이 카테고리를 합쳐 그린다면 그쪽에서 한 번 더 세워야 한다.
     */
    private static List<AchievementResDTO.AchievementItem> byRemainingWork(
            List<AchievementResDTO.AchievementItem> items
    ) {
        return items.stream()
                .sorted(Comparator.comparingInt(
                        (AchievementResDTO.AchievementItem item) -> switch (item.status()) {
                            case ACHIEVED -> 0;
                            case IN_PROGRESS -> 1;
                            case CLAIMED -> 2;
                        }))
                .toList();
    }

    /**
     * 히든 업적인데 아직 달성 전인가. 그렇다면 그 회원에게는 <b>존재하지 않는 것으로 다룬다</b>.
     *
     * <p>달성 판정 자체는 그대로 돈다 — 목록에 없다고 진행도가 안 쌓이면 영영 달성할 수 없다.
     * 여기서 정하는 것은 "보여 주는가" 하나뿐이다.
     */
    private static boolean isUndiscoveredHidden(Achievement achievement, MemberAchievement memberAchievement) {
        if (!achievement.isHiddenYn()) {
            return false;
        }
        return memberAchievement == null
                || memberAchievement.getStatus() == MemberAchievementStatus.IN_PROGRESS;
    }

    /**
     * 상단 요약 카드. 획득/전체, 진행 중, 스페셜(UNIQUE 카테고리) 획득/전체를 센다.
     *
     * <p>"진행 중"은 아직 미달성이면서 진행도가 0보다 큰 업적만 센다. COMPOSITE 업적은
     * {@code currentProgress} 컬럼을 쓰지 않으므로 member_achievement 행이 있다는 것
     * 자체를 "손을 댄 적 있다"로 보고 진행 중에 포함한다.
     */
    private static AchievementResDTO.Summary toSummary(
            List<Achievement> allAchievements,
            Map<Long, MemberAchievement> memberAchievementByAchievementId
    ) {
        int acquired = 0;
        int inProgress = 0;
        int specialTotal = 0;
        int specialAcquired = 0;

        for (Achievement achievement : allAchievements) {
            MemberAchievement memberAchievement = memberAchievementByAchievementId.get(achievement.getId());
            boolean isSpecial = achievement.getCategory() == AchievementCategory.UNIQUE;
            if (isSpecial) {
                specialTotal++;
            }

            if (memberAchievement == null) {
                continue;
            }
            MemberAchievementStatus status = memberAchievement.getStatus();
            if (status == MemberAchievementStatus.ACHIEVED || status == MemberAchievementStatus.CLAIMED) {
                acquired++;
                if (isSpecial) {
                    specialAcquired++;
                }
            } else if (achievement.getProgressType() == AchievementProgressType.COMPOSITE
                    || memberAchievement.getCurrentProgress() > 0) {
                inProgress++;
            }
        }

        return AchievementResDTO.Summary.builder()
                .acquiredCount(acquired)
                .totalCount(allAchievements.size())
                .inProgressCount(inProgress)
                .specialAcquiredCount(specialAcquired)
                .specialTotalCount(specialTotal)
                .build();
    }

    private static AchievementResDTO.AchievementItem toItem(
            Achievement achievement,
            MemberAchievement memberAchievement,
            Map<Long, List<MemberAchievementCondition>> conditionProgressByMemberAchievementId,
            Map<Long, String> badgeImageUrlByAchievementId
    ) {
        MemberAchievementStatus status = memberAchievement != null
                ? memberAchievement.getStatus()
                : MemberAchievementStatus.IN_PROGRESS;

        // 히든 가리기(이름을 "숨겨진 업적" 으로 바꾸고 진행도를 지우던 처리)는 없앴다.
        // 아직 달성 전인 히든은 toAchievements 가 목록에서 통째로 빼므로 여기 도달하지 않는다.
        AchievementResDTO.Progress progress = null;
        List<AchievementResDTO.ConditionProgress> conditionProgresses = List.of();

        if (achievement.getProgressType() == AchievementProgressType.COMPOSITE) {
            List<MemberAchievementCondition> progresses = memberAchievement != null
                    ? conditionProgressByMemberAchievementId.getOrDefault(memberAchievement.getId(), List.of())
                    : List.of();
            Map<String, Integer> currentByKey = progresses.stream()
                    .collect(Collectors.toMap(MemberAchievementCondition::getConditionKey,
                            MemberAchievementCondition::getCurrentValue));

            conditionProgresses = achievement.getConditions().stream()
                    .sorted(Comparator.comparingInt(AchievementCondition::getSortOrder))
                    .map(c -> AchievementResDTO.ConditionProgress.builder()
                            .conditionKey(c.getConditionKey())
                            .current(currentByKey.getOrDefault(c.getConditionKey(), 0))
                            .target(c.getTargetCount())
                            .build())
                    .toList();
        } else if (achievement.getProgressType() != AchievementProgressType.NONE) {
            int current = memberAchievement != null ? memberAchievement.getCurrentProgress() : 0;
            progress = AchievementResDTO.Progress.builder()
                    .current(current)
                    .target(achievement.getTargetCount())
                    .build();
        }

        return AchievementResDTO.AchievementItem.builder()
                .achievementId(achievement.getId())
                .code(achievement.getCode())
                .name(achievement.getName())
                .conditionDesc(achievement.getConditionDesc())
                .status(status)
                .progress(progress)
                .conditionProgresses(conditionProgresses)
                .topazReward(achievement.getTopazReward())
                .badgeYn(achievement.isBadgeYn())
                .limitedOutfitYn(achievement.isLimitedOutfitYn())
                .hiddenYn(achievement.isHiddenYn())
                .badgeImageUrl(badgeImageUrlByAchievementId.get(achievement.getId()))
                .achievedAt(memberAchievement != null ? memberAchievement.getAchievedAt() : null)
                .claimedAt(memberAchievement != null ? memberAchievement.getClaimedAt() : null)
                .build();
    }
}
