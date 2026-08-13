package com.lirouti.domain.character.enums;

import com.lirouti.domain.challenge.enums.ChallengeCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 해금 조건이 쓰는 카테고리 논리 키.
 *
 * <p><b>조건에 카테고리 id 를 넣지 않는 이유가 여기 있다.</b> 같은 "운동" 이 세 곳에 따로
 * 있고 체계가 다르다 — 개인·그룹은 프리셋 행(id)이고 챌린지는 enum 이다. 조건이 어느 한
 * 체계의 값을 들고 있으면 나머지 둘을 셀 수 없다.
 *
 * <p>개인과 그룹은 프리셋 id 가 같은 번호를 쓴다(운영에서 확인). 사용자·그룹이 만든 카테고리는
 * 이름이 같아도 세지 않는다 — 운영에 {@code [테스트]사이드} 같은 것이 실제로 있다.
 */
@Getter
@RequiredArgsConstructor
public enum RoutineCategoryKey {

    EXERCISE(1L, ChallengeCategory.EXERCISE),
    HEALTH(2L, ChallengeCategory.HEALTH),
    /** 자기계발. 챌린지 쪽 이름만 {@code STUDY} 로 다르다. */
    SELF_DEV(3L, ChallengeCategory.STUDY),
    /** 생활정리. */
    LIFE(4L, ChallengeCategory.LIFE),
    MIND(5L, ChallengeCategory.MIND),
    HOBBY(6L, ChallengeCategory.HOBBY);

    /** 개인·그룹 루틴 카테고리의 프리셋 id. */
    private final Long presetCategoryId;

    private final ChallengeCategory challengeCategory;

    /** 모르는 키는 비어 있다 — 조건 시드에 오타가 있어도 판정이 터지지 않고 미달로 남는다. */
    public static Optional<RoutineCategoryKey> from(String name) {
        return Arrays.stream(values())
                .filter(key -> key.name().equalsIgnoreCase(name))
                .findFirst();
    }
}
