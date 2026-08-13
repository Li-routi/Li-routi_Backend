package com.lirouti.domain.character.service.query;

import com.lirouti.domain.character.converter.CharacterConverter;
import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.domain.character.entity.MemberCharacter;
import com.lirouti.domain.character.repository.AvatarCharacterRepository;
import com.lirouti.domain.character.repository.MemberCharacterRepository;
import com.lirouti.domain.character.repository.MemberSelectedCharacterRepository;
import com.lirouti.domain.character.entity.MemberSelectedCharacter;
import com.lirouti.domain.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
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

        Long selectedCharacterId = memberSelectedCharacterRepository.findByMemberId(memberId)
                .map(MemberSelectedCharacter::getCharacterId)
                .orElse(null);

        return CharacterConverter.toCharacters(
                avatarCharacterRepository.findAllByActiveTrueAndHiddenFalseOrderByDisplayOrderAsc(),
                ownedByCharacterId,
                selectedCharacterId,
                mediaService::resolveAvatarAssetUrl);
    }
}
