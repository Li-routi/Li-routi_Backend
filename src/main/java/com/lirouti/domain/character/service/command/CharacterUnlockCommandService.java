package com.lirouti.domain.character.service.command;

import com.lirouti.domain.character.entity.AvatarCharacter;
import com.lirouti.domain.character.entity.CharacterUnlockCondition;
import com.lirouti.domain.character.entity.MemberCharacter;
import com.lirouti.domain.character.entity.MemberSelectedCharacter;
import com.lirouti.domain.character.repository.AvatarCharacterRepository;
import com.lirouti.domain.character.repository.CharacterUnlockConditionRepository;
import com.lirouti.domain.character.repository.MemberCharacterRepository;
import com.lirouti.domain.character.repository.MemberSelectedCharacterRepository;
import com.lirouti.domain.character.service.evaluator.UnlockConditionEvaluator;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.popup.service.command.PopupCommandService;
import com.lirouti.domain.popup.service.command.PopupPublishCommand;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 조건을 세서 캐릭터를 연다.
 *
 * <p><b>인증이 저장되는 트랜잭션 안에서 부른다.</b> 나누면 해금은 됐는데 팝업이 없거나,
 * 인증은 남았는데 해금이 빠진 상태가 생긴다.
 */
@Slf4j
@Service
public class CharacterUnlockCommandService {

    private static final String POPUP_TYPE = "CHARACTER_UNLOCKED";
    private static final String REFERENCE_TYPE = "CHARACTER";

    private final AvatarCharacterRepository avatarCharacterRepository;
    private final CharacterUnlockConditionRepository characterUnlockConditionRepository;
    private final MemberCharacterRepository memberCharacterRepository;
    private final MemberSelectedCharacterRepository memberSelectedCharacterRepository;
    private final MemberRepository memberRepository;
    private final PopupCommandService popupCommandService;
    private final Clock clock;

    /** 조건 키로 판정기를 찾는다. 판정 한 번에 조건이 여럿이라 매번 만들지 않는다. */
    private final Map<String, UnlockConditionEvaluator> evaluatorsByKey;

    public CharacterUnlockCommandService(
            AvatarCharacterRepository avatarCharacterRepository,
            CharacterUnlockConditionRepository characterUnlockConditionRepository,
            MemberCharacterRepository memberCharacterRepository,
            MemberSelectedCharacterRepository memberSelectedCharacterRepository,
            MemberRepository memberRepository,
            PopupCommandService popupCommandService,
            List<UnlockConditionEvaluator> evaluators,
            Clock clock
    ) {
        this.avatarCharacterRepository = avatarCharacterRepository;
        this.characterUnlockConditionRepository = characterUnlockConditionRepository;
        this.memberCharacterRepository = memberCharacterRepository;
        this.memberSelectedCharacterRepository = memberSelectedCharacterRepository;
        this.memberRepository = memberRepository;
        this.popupCommandService = popupCommandService;
        this.clock = clock;
        this.evaluatorsByKey = evaluators.stream().collect(Collectors.toMap(
                UnlockConditionEvaluator::conditionKey, Function.identity()));
    }

    /**
     * 아직 못 연 캐릭터의 조건을 훑어 다 찬 것을 연다.
     *
     * <p><b>조건 행이 하나도 없으면 항상 열린다.</b> 기본 캐릭터가 그렇게 표현된다 — 가입
     * 직후 이 판정이 한 번 돌면 루티가 그 자리에서 들어온다.
     *
     * <p><b>여는 것과 팝업을 같은 트랜잭션에 둔다.</b> 나누면 열렸는데 무엇이 열렸는지
     * 사용자가 모르는 상태가 남는다. 다만 기본 캐릭터는 팝업을 띄우지 않는다 — 가입하자마자
     * "새 친구가 왔어요" 가 뜨면 무엇을 해서 얻었는지 알 수 없다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluateAndUnlock(Long memberId) {
        Set<Long> owned = Set.copyOf(memberCharacterRepository.findCharacterIdsByMemberId(memberId));
        List<AvatarCharacter> candidates = avatarCharacterRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .filter(character -> !owned.contains(character.getId()))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        Map<Long, List<CharacterUnlockCondition>> conditionsByCharacterId =
                characterUnlockConditionRepository.findAllOfActiveCharacters().stream()
                        .collect(Collectors.groupingBy(
                                condition -> condition.getAvatarCharacter().getId()));

        Member member = null;
        for (AvatarCharacter character : candidates) {
            List<CharacterUnlockCondition> conditions =
                    conditionsByCharacterId.getOrDefault(character.getId(), List.of());
            if (!isSatisfied(memberId, conditions)) {
                continue;
            }

            if (member == null) {
                member = memberRepository.findById(memberId)
                        .orElseThrow(() -> new GeneralException(MemberErrorCode.MEMBER_NOT_FOUND));
            }
            unlock(member, character, conditions.isEmpty());
        }
    }

    /**
     * 한 캐릭터의 조건이 전부 찼는가.
     *
     * <p><b>행이 여럿이면 AND 다.</b> "운동 또는 건강" 같은 합집합은 한 행의
     * {@code condition_param} 안에서 표현한다.
     */
    private boolean isSatisfied(Long memberId, List<CharacterUnlockCondition> conditions) {
        return conditions.stream().allMatch(condition -> {
            UnlockConditionEvaluator evaluator = evaluatorsByKey.get(condition.getConditionKey());
            if (evaluator == null) {
                // 판정기가 없는 키가 데이터에 있을 수 있다(백오피스). 터뜨리면 인증 자체가
                // 실패하므로 조용히 미달로 본다.
                log.debug("판정기가 없는 해금 조건을 건너뜁니다. conditionKey={}",
                        condition.getConditionKey());
                return false;
            }
            return evaluator.count(memberId, condition.getConditionParam())
                    >= condition.getTargetCount();
        });
    }

    /**
     * 보유 행을 만들고, 처음이면 선택까지 채운다.
     *
     * <p><b>유니크가 승자를 가린다.</b> 판정이 동시에 두 번 돌아도 두 번째 INSERT 가 튕기고
     * 그 트랜잭션이 되돌아간다 — 팝업도 함께 사라지므로 둘이 뜨지 않는다.
     */
    private void unlock(Member member, AvatarCharacter character, boolean isDefaultCharacter) {
        if (memberCharacterRepository.existsByMemberIdAndAvatarCharacterId(
                member.getId(), character.getId())) {
            return;
        }

        memberCharacterRepository.save(MemberCharacter.builder()
                .member(member)
                .avatarCharacter(character)
                .unlockedDate(LocalDate.now(clock))
                .build());

        // 회원에게 선택된 캐릭터가 항상 있게 한다. 비워 두면 "선택 없음" 을 어떻게 그릴지
        // 규칙이 하나 더 필요해진다. 복합 외래 키가 보유 행을 요구하므로 순서는 보유 → 선택이다.
        if (!memberSelectedCharacterRepository.existsByMemberId(member.getId())) {
            memberSelectedCharacterRepository.save(MemberSelectedCharacter.builder()
                    .memberId(member.getId())
                    .characterId(character.getId())
                    .build());
        }

        if (isDefaultCharacter) {
            return;
        }

        popupCommandService.publish(new PopupPublishCommand(
                member.getId(),
                POPUP_TYPE,
                "새 친구가 왔어요",
                character.getName() + "를 만났어요",
                character.getAdultImageKey(),
                REFERENCE_TYPE,
                character.getId(),
                // 같은 사건이면 언제 계산해도 같은 문자열이어야 한다.
                "character-unlocked:" + character.getId()
        ));
        log.info("캐릭터를 해금했습니다. memberId={}, characterCode={}",
                member.getId(), character.getCode());
    }
}
