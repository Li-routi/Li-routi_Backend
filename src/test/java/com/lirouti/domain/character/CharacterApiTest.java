package com.lirouti.domain.character;

import com.lirouti.domain.activity.repository.MemberActivityDayRepository;
import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.domain.character.enums.AvatarLayer;
import com.lirouti.domain.character.exception.CharacterException;
import com.lirouti.domain.character.service.command.CharacterSelectionCommandService;
import com.lirouti.domain.character.service.command.CharacterUnlockCommandService;
import com.lirouti.domain.character.service.query.AvatarLayerAssembler;
import com.lirouti.domain.character.service.query.CharacterQueryService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 캐릭터 조회·선택과 레이어 조립.
 *
 * <p>여기서 지키는 것은 셋이다 — <b>알과 성체는 서버가 고른다</b>, <b>안 연 것은 못 고른다</b>,
 * <b>레이어는 정렬돼서 나간다</b>.
 */
@SpringBootTest
@Transactional
@DisplayName("캐릭터 API 테스트")
class CharacterApiTest {

    private static final long ROUTI = 1L;
    private static final long NOA = 2L;
    private static final long MINT = 6L;

    @Autowired
    private CharacterQueryService characterQueryService;
    @Autowired
    private CharacterSelectionCommandService characterSelectionCommandService;
    @Autowired
    private CharacterUnlockCommandService characterUnlockCommandService;
    @Autowired
    private AvatarLayerAssembler avatarLayerAssembler;
    @Autowired
    private MemberActivityDayRepository memberActivityDayRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();
    private Member me;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("charapi" + n + "@ex.com").nickname("charapi" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("charapi-sid-" + n).build();
        em.persist(me);
        em.flush();
    }

    private void unlock() {
        characterUnlockCommandService.evaluateAndUnlock(me.getId());
        em.flush();
        em.clear();
    }

    private CharacterResDTO.Character find(long characterId) {
        return characterQueryService.getCharacters(me.getId()).characters().stream()
                .filter(character -> character.id().equals(characterId))
                .findFirst().orElseThrow();
    }

    // ── 목록 ──

    @Test
    @DisplayName("안 연 캐릭터는 알 그림, 연 캐릭터는 성체 그림이 나간다")
    void getCharacters_ServerPicksEggOrAdult() {
        unlock();

        assertAll(
                () -> assertThat(find(ROUTI).unlocked()).isTrue(),
                () -> assertThat(find(ROUTI).imageUrl()).endsWith("adult-v1.png"),
                () -> assertThat(find(NOA).unlocked()).isFalse(),
                () -> assertThat(find(NOA).imageUrl())
                        .as("앱이 고르지 않도록 서버가 알 그림을 내린다").endsWith("egg-v1.png"));
    }

    @Test
    @DisplayName("첫 캐릭터가 선택된 상태로 나간다")
    void getCharacters_MarksSelected() {
        unlock();

        assertThat(characterQueryService.getCharacters(me.getId()).characters())
                .filteredOn(CharacterResDTO.Character::selected)
                .singleElement()
                .satisfies(character -> assertThat(character.id()).isEqualTo(ROUTI));
    }

    // ── 선택 ──

    @Test
    @DisplayName("보유한 캐릭터로 바꿀 수 있다")
    void select_ChangesToOwnedCharacter() {
        memberActivityDayRepository.record(me.getId(), LocalDate.of(2026, 8, 13), false);
        unlock();

        characterSelectionCommandService.select(me.getId(), MINT);
        em.flush();
        em.clear();

        assertAll(
                () -> assertThat(find(MINT).selected()).isTrue(),
                () -> assertThat(find(ROUTI).selected()).isFalse());
    }

    /** 알 상태를 고를 수 있으면 화면에 열지도 않은 캐릭터가 뜬다. */
    @Test
    @DisplayName("안 연 캐릭터는 고를 수 없다")
    void select_RejectsLockedCharacter() {
        unlock();

        assertThatThrownBy(() -> characterSelectionCommandService.select(me.getId(), NOA))
                .isInstanceOf(CharacterException.class);
    }

    @Test
    @DisplayName("같은 것을 다시 골라도 성공이다")
    void select_IsIdempotent() {
        unlock();

        characterSelectionCommandService.select(me.getId(), ROUTI);
        characterSelectionCommandService.select(me.getId(), ROUTI);
        em.flush();

        assertThat(find(ROUTI).selected()).isTrue();
    }

    /**
     * 캐릭터를 주는 경로가 둘이다 — 조건 판정(선택까지 채운다)과 업적 claim(보유만 넣는다).
     * 뒤엣것으로 첫 캐릭터를 얻으면 선택이 비어 있는데, 그대로 두면 캐릭터도 둥지도 안 그려져
     * 사용자는 "얻었는데 아무것도 안 바뀌었다" 를 보게 된다.
     */
    @Test
    @DisplayName("선택이 비어 있어도 보유가 있으면 그것으로 그린다")
    void fallsBackToOwnedWhenSelectionMissing() {
        unlock();
        // 업적 경로처럼 선택만 지운다. 보유는 그대로다.
        em.createNativeQuery("delete from member_selected_character where member_id = :memberId")
                .setParameter("memberId", me.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        assertAll(
                () -> assertThat(avatarLayerAssembler.assembleAsResponse(me.getId(), List.of()))
                        .as("아바타에 캐릭터가 빠지면 안 된다").isNotEmpty(),
                () -> assertThat(find(ROUTI).selected())
                        .as("도감과 아바타가 같은 캐릭터를 가리켜야 한다").isTrue());
    }

    // ── 레이어 ──

    /**
     * 캐릭터가 둥지 사이에 끼어야 한다. 순서가 어긋나면 캐릭터가 둥지에 가리거나 둥지가
     * 캐릭터 뒤로 사라진다.
     */
    @Test
    @DisplayName("레이어는 뒷둥지·캐릭터·윗둥지 순으로 정렬돼 나간다")
    void layers_AreSortedAndSandwichCharacter() {
        unlock();

        List<CharacterResDTO.Layer> layers =
                avatarLayerAssembler.assembleAsResponse(me.getId(), List.of());

        assertAll(
                () -> assertThat(layers).extracting(CharacterResDTO.Layer::layer)
                        .containsExactly(AvatarLayer.NEST_BACK, AvatarLayer.CHARACTER,
                                AvatarLayer.NEST_FRONT),
                () -> assertThat(layers).extracting(CharacterResDTO.Layer::z).isSorted(),
                () -> assertThat(layers).allSatisfy(layer ->
                        assertThat(layer.imageUrl()).startsWith("http")));
    }

    /** 캐릭터 없이 둥지만 뜨면 빈 둥지가 남는다. */
    @Test
    @DisplayName("캐릭터가 없으면 둥지도 그리지 않는다")
    void layers_EmptyWithoutCharacter() {
        assertThat(avatarLayerAssembler.assembleAsResponse(me.getId(), List.of())).isEmpty();
    }

    /** 완수한 날이 창을 채우지 못하면 레벨 1 이다. 기본값 15 일 기준. */
    @Test
    @DisplayName("연속이 모자라면 둥지는 레벨 1 이다")
    void layers_NestStaysLevelOneWithoutStreak() {
        unlock();

        List<CharacterResDTO.Layer> layers =
                avatarLayerAssembler.assembleAsResponse(me.getId(), List.of());

        assertThat(layers).filteredOn(layer -> layer.layer() == AvatarLayer.NEST_BACK)
                .singleElement()
                .satisfies(layer -> assertThat(layer.imageUrl()).contains("level1-back"));
    }
}
