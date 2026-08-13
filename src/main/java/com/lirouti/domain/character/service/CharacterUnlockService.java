package com.lirouti.domain.character.service;

import com.lirouti.domain.character.entity.AvatarCharacter;
import com.lirouti.domain.character.entity.CharacterUnlockCondition;
import com.lirouti.domain.character.repository.CharacterUnlockConditionRepository;
import com.lirouti.domain.character.repository.MemberCharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * achievement claim을 캐릭터알 해금 판정의 트리거로 쓴다.
 *
 * <p>ACHIEVEMENT_CLAIMED 조건은 targetCount를 보지 않고 "이 업적을 claim했다"는 사실
 * 자체가 곧 조건 충족이다 — achievement 도메인이 이미 진행도/달성 여부를 판정 끝낸
 * 상태에서 여기 도달하므로 다시 카운트할 필요가 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterUnlockService {

    private final CharacterUnlockConditionRepository characterUnlockConditionRepository;
    private final MemberCharacterRepository memberCharacterRepository;

    @Transactional
    public void unlockByAchievementClaim(Long memberId, String achievementCode) {
        List<CharacterUnlockCondition> conditions =
                characterUnlockConditionRepository.findByAchievementCode(achievementCode);

        for (CharacterUnlockCondition condition : conditions) {
            AvatarCharacter character = condition.getAvatarCharacter();
            memberCharacterRepository.insertIfAbsent(memberId, character.getId());
        }
    }
}