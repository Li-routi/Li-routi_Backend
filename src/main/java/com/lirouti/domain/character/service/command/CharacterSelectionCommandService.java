package com.lirouti.domain.character.service.command;

import com.lirouti.domain.character.entity.MemberSelectedCharacter;
import com.lirouti.domain.character.exception.CharacterException;
import com.lirouti.domain.character.exception.code.error.CharacterErrorCode;
import com.lirouti.domain.character.repository.MemberCharacterRepository;
import com.lirouti.domain.character.repository.MemberSelectedCharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterSelectionCommandService {

    private final MemberSelectedCharacterRepository memberSelectedCharacterRepository;
    private final MemberCharacterRepository memberCharacterRepository;

    /**
     * 쓸 캐릭터를 고른다.
     *
     * <p><b>보유하지 않은 캐릭터는 고를 수 없다.</b> 복합 외래 키가 {@code member_character} 를
     * 참조하므로 제약만으로도 막히지만, 그때 나오는 것은 데이터 무결성 오류라 사용자에게
     * 보여줄 말이 아니다. 여기서 먼저 확인해 404 로 답한다.
     *
     * <p><b>없는 캐릭터와 안 연 캐릭터를 가르지 않는다.</b> 가르면 id 를 넣어 보는 것만으로
     * 어떤 캐릭터가 존재하는지 알 수 있다.
     *
     * <p>이미 고른 것을 다시 골라도 성공이다 — 화면에서 같은 것을 두 번 누르는 것이 오류일
     * 이유가 없다. 따닥으로 두 번 들어와도 마찬가지다: 회원당 한 행이라(기본 키) 갱신이 곧
     * 교체이고, 첫 선택에서 두 요청이 겹쳐도 upsert 가 뒤엣것을 살린다.
     */
    @Transactional
    public void select(Long memberId, Long characterId) {
        if (!memberCharacterRepository.existsByMemberIdAndAvatarCharacterId(memberId, characterId)) {
            throw new CharacterException(CharacterErrorCode.CHARACTER_NOT_OWNED);
        }

        memberSelectedCharacterRepository.upsert(memberId, characterId);
    }
}
