package com.lirouti.domain.character.service.query;

import com.lirouti.domain.character.converter.CharacterConverter;
import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.domain.character.entity.AvatarCharacter;
import com.lirouti.domain.character.entity.MemberCharacter;
import com.lirouti.domain.character.repository.AvatarCharacterRepository;
import com.lirouti.domain.character.repository.MemberCharacterRepository;
import com.lirouti.domain.character.repository.MemberSelectedCharacterRepository;
import com.lirouti.domain.character.entity.MemberSelectedCharacter;
import com.lirouti.domain.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CharacterQueryService {

    private final AvatarCharacterRepository avatarCharacterRepository;
    private final MemberCharacterRepository memberCharacterRepository;
    private final MemberSelectedCharacterRepository memberSelectedCharacterRepository;
    private final MediaService mediaService;

    /**
     * 캐릭터 도감.
     *
     * <p><b>감춘 캐릭터도 목록에 넣지 않는다</b>({@code hidden}) — 지금 시드는 전부 0 이라
     * 실질적으로 열셋이 다 나온다.
     *
     * <p>안 연 것은 알 그림으로 나간다. <b>어느 그림을 쓸지는 서버가 정한다.</b>
     */
    @Transactional(readOnly = true)
    public CharacterResDTO.Characters getCharacters(Long memberId) {
        Map<Long, MemberCharacter> ownedByCharacterId =
                memberCharacterRepository.findAllByMemberId(memberId).stream()
                        .collect(Collectors.toMap(
                                owned -> owned.getAvatarCharacter().getId(), Function.identity()));

        // 선택이 비어 있으면 가장 먼저 얻은 것을 선택으로 본다. 업적 claim 으로 캐릭터를
        // 받으면 보유만 생기고 선택은 비어 있는데, 그때 도감과 아바타가 서로 다른 캐릭터를
        // 가리키면 안 된다 — 아바타도 같은 규칙으로 그린다(AvatarLayerAssembler).
        Long selectedCharacterId = memberSelectedCharacterRepository.findByMemberId(memberId)
                .map(MemberSelectedCharacter::getCharacterId)
                .orElseGet(() -> ownedByCharacterId.values().stream()
                        .min(java.util.Comparator.comparing(MemberCharacter::getId))
                        .map(owned -> owned.getAvatarCharacter().getId())
                        .orElse(null));

        return CharacterConverter.toCharacters(
                listedFor(ownedByCharacterId.keySet()),
                ownedByCharacterId,
                selectedCharacterId,
                mediaService::resolveAvatarAssetUrl);
    }

    /**
     * 도감에 실을 캐릭터. <b>보유한 것은 감춰졌거나 내려갔어도 실린다.</b>
     *
     * <p>목록만 {@code active}·{@code hidden} 으로 거르면, 운영이 캐릭터 하나를 내렸을 때 그것을
     * 쓰고 있던 사람의 도감에서 <b>선택된 항목이 사라진다</b> — 어느 줄에도 {@code selected} 가
     * 붙지 않는데 아바타에는 그 캐릭터가 계속 그려진다. 도감과 아바타가 서로 다른 것을 가리키는
     * 상태다.
     *
     * <p>내리는 것은 <b>새로 얻지 못하게</b> 하는 조치이지 이미 가진 것을 빼앗는 조치가 아니다.
     *
     * <p>대개 추가 조회가 없다 — 보유한 캐릭터는 거의 언제나 목록 안에 있다.
     */
    private List<AvatarCharacter> listedFor(Set<Long> ownedCharacterIds) {
        List<AvatarCharacter> listed =
                avatarCharacterRepository.findAllByActiveTrueAndHiddenFalseOrderByDisplayOrderAsc();

        Set<Long> listedIds = listed.stream().map(AvatarCharacter::getId).collect(Collectors.toSet());
        List<Long> missingOwned = ownedCharacterIds.stream()
                .filter(characterId -> !listedIds.contains(characterId))
                .toList();
        if (missingOwned.isEmpty()) {
            return listed;
        }

        List<AvatarCharacter> merged = new ArrayList<>(listed);
        merged.addAll(avatarCharacterRepository.findAllById(missingOwned));
        merged.sort(Comparator.comparingInt(AvatarCharacter::getDisplayOrder)
                .thenComparing(AvatarCharacter::getId));
        return merged;
    }
}
