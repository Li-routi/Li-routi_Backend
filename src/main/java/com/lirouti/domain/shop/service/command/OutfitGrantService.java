package com.lirouti.domain.shop.service.command;

import com.lirouti.domain.achievement.entity.AchievementRewardItem;
import com.lirouti.domain.achievement.repository.AchievementRewardItemRepository;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.repository.AvatarItemRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 업적 보상으로 한정 의상을 지급한다. achievement_reward_item(item_category='OUTFIT')이
 * 가리키는 avatar_item을 그대로 지급한다 - 구매(AvatarPurchase)를 거치지 않으므로
 * avatarPurchase는 null로 남긴다.
 */
@Service
@RequiredArgsConstructor
public class OutfitGrantService {

    private static final String GRANT_REASON_ACHIEVEMENT_REWARD = "ACHIEVEMENT_REWARD";

    private final AchievementRewardItemRepository achievementRewardItemRepository;
    private final AvatarItemRepository avatarItemRepository;
    private final MemberAvatarItemRepository memberAvatarItemRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void grant(Long memberId, String achievementCode) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId가 유효하지 않습니다. memberId=" + memberId);
        }
        if (!StringUtils.hasText(achievementCode)) {
            throw new IllegalArgumentException("achievementCode가 유효하지 않습니다.");
        }

        List<AchievementRewardItem> outfitItems = achievementRewardItemRepository
                .findOutfitItemsByAchievementCode(achievementCode);

        for (AchievementRewardItem rewardItem : outfitItems) {
            if (rewardItem.getItemRefId() == null) {
                continue; // 아직 실제 아이템에 연결 안 된 참고용 행 - 방어
            }
            grantOne(memberId, rewardItem.getItemRefId());
        }
    }

    /**
     * exists 확인 후 save 하는 대신 INSERT IGNORE 로 한 번에 처리한다. 동시 요청 두 개가
     * 나란히 "안 가지고 있음"을 확인하고 둘 다 저장을 시도하면, exists+save 방식에서는
     * 하나가 uk_member_avatar_item_member_item 위반으로 실패해 트랜잭션이 rollback-only가
     * 된다. INSERT IGNORE 는 그 경합을 DB 단에서 흡수한다 - 이미 있으면 0행, 처음이면 1행,
     * 어느 쪽이든 멱등하게 성공으로 끝난다.
     */
    private void grantOne(Long memberId, Long avatarItemId) {
        AvatarItem item = avatarItemRepository.findById(avatarItemId)
                .orElseThrow(() -> new IllegalStateException(
                        "achievement_reward_item이 가리키는 avatar_item이 없습니다. id=" + avatarItemId));

        memberAvatarItemRepository.insertIfAbsent(
                memberId,
                avatarItemId,
                item.getCurrency().name(),
                GRANT_REASON_ACHIEVEMENT_REWARD
        );
    }
}
