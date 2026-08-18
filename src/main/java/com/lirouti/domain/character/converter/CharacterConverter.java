package com.lirouti.domain.character.converter;

import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.domain.character.entity.AvatarCharacter;
import com.lirouti.domain.character.entity.MemberCharacter;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class CharacterConverter {

    private CharacterConverter() {
    }

    /**
     * <b>알과 성체 중 하나를 서버가 골라 내린다.</b> 앱이 고르게 하면 "해금됐는데 알이 뜨는"
     * 어긋남이 화면마다 따로 생길 수 있다.
     */
    public static CharacterResDTO.Character toCharacter(AvatarCharacter character,
                                                        MemberCharacter owned,
                                                        boolean selected,
                                                        UnaryOperator<String> toViewUrl) {
        boolean unlocked = owned != null;
        return CharacterResDTO.Character.builder()
                .id(character.getId())
                .code(character.getCode())
                .name(character.getName())
                .unlocked(unlocked)
                .selected(selected)
                .imageUrl(toViewUrl.apply(unlocked
                        ? character.getAdultImageKey()
                        : character.getEggImageKey()))
                .unlockedDate(unlocked ? owned.getUnlockedDate() : null)
                .build();
    }

    public static CharacterResDTO.Characters toCharacters(List<AvatarCharacter> characters,
                                                          Map<Long, MemberCharacter> ownedByCharacterId,
                                                          Long selectedCharacterId,
                                                          UnaryOperator<String> toViewUrl) {
        return CharacterResDTO.Characters.builder()
                .characters(characters.stream()
                        .map(character -> toCharacter(
                                character,
                                ownedByCharacterId.get(character.getId()),
                                character.getId().equals(selectedCharacterId),
                                toViewUrl))
                        .toList())
                .build();
    }
}
