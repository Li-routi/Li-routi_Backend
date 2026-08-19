package com.lirouti.domain.shop.service.command;

import com.lirouti.domain.achievement.entity.AchievementRewardItem;
import com.lirouti.domain.achievement.repository.AchievementRewardItemRepository;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarItem;
import com.lirouti.domain.shop.repository.AvatarItemRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        List<AchievementRewardItem> outfitItems = achievementRewardItemRepository
                .findOutfitItemsByAchievementCode(achievementCode);

        for (AchievementRewardItem rewardItem : outfitItems) {
            if (rewardItem.getItemRefId() == null) {
                continue; // 아직 실제 아이템에 연결 안 된 참고용 행 - 방어
            }
            grantOne(memberId, rewardItem.getItemRefId());
        }
    }

    private void grantOne(Long memberId, Long avatarItemId) {
        if (memberAvatarItemRepository.existsByMemberIdAndAvatarItemId(memberId, avatarItemId)) {
            return; // 이미 보유 - 멱등 (이미 이 의상을 상점에서 샀거나 다른 경로로 받은 경우도 포함)
        }
        AvatarItem item = avatarItemRepository.findById(avatarItemId)
                .orElseThrow(() -> new IllegalStateException(
                        "achievement_reward_item이 가리키는 avatar_item이 없습니다. id=" + avatarItemId));

        memberAvatarItemRepository.save(
                MemberAvatarItem.builder()
                        .member(memberRepository.getReferenceById(memberId))
                        .avatarItem(item)
                        .avatarPurchase(null)
                        .currency(item.getCurrency())
                        .paidPrice(0)
                        .purchasedAt(LocalDateTime.now())
                        .grantReason(GRANT_REASON_ACHIEVEMENT_REWARD)
                        .build()
        );
    }
}
