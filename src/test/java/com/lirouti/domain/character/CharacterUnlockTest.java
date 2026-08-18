package com.lirouti.domain.character;

import com.lirouti.domain.activity.repository.MemberActivityDayRepository;
import com.lirouti.domain.character.repository.MemberCharacterRepository;
import com.lirouti.domain.character.repository.MemberSelectedCharacterRepository;
import com.lirouti.domain.character.service.command.CharacterUnlockCommandService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.popup.service.query.PopupQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 캐릭터 해금 판정.
 *
 * <p>시드에 실린 조건을 그대로 쓴다 — 루티는 조건이 없고(기본), 민트는 활동일 1 일,
 * 솔라는 연속 100 일이다. 조건 시드가 바뀌면 이 테스트가 먼저 알려 준다.
 */
@SpringBootTest
@Transactional
@DisplayName("캐릭터 해금 테스트")
class CharacterUnlockTest {

    private static final long ROUTI = 1L;
    private static final long NOA = 2L;
    private static final long MINT = 6L;
    private static final long SOLA = 7L;

    @Autowired
    private CharacterUnlockCommandService characterUnlockCommandService;
    @Autowired
    private MemberCharacterRepository memberCharacterRepository;
    @Autowired
    private MemberSelectedCharacterRepository memberSelectedCharacterRepository;
    @Autowired
    private MemberActivityDayRepository memberActivityDayRepository;
    @Autowired
    private PopupQueryService popupQueryService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();
    private Member me;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("unlock" + n + "@ex.com").nickname("unlock" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("unlock-sid-" + n).build();
        em.persist(me);
        em.flush();
    }

    private void unlock() {
        characterUnlockCommandService.evaluateAndUnlock(me.getId());
        em.flush();
        em.clear();
    }

    /** 조건 행이 하나도 없는 캐릭터가 곧 기본이다. "가입 시 지급" 을 따로 구현하지 않는다. */
    @Test
    @DisplayName("조건이 없는 캐릭터는 판정 한 번에 들어온다")
    void unlock_GivesDefaultCharacter() {
        unlock();

        assertThat(memberCharacterRepository.findCharacterIdsByMemberId(me.getId()))
                .contains(ROUTI);
    }

    /** 가입하자마자 "새 친구가 왔어요" 가 뜨면 무엇을 해서 얻었는지 알 수 없다. */
    @Test
    @DisplayName("기본 캐릭터는 팝업을 띄우지 않는다")
    void unlock_DefaultCharacterHasNoPopup() {
        unlock();

        assertThat(popupQueryService.getPending(me.getId()).popups()).isEmpty();
    }

    @Test
    @DisplayName("첫 캐릭터를 얻으면 선택까지 채워진다 — 선택 없는 상태를 두지 않는다")
    void unlock_FillsSelection() {
        unlock();

        assertThat(memberSelectedCharacterRepository.existsByMemberId(me.getId())).isTrue();
    }

    @Test
    @DisplayName("조건을 못 채우면 열리지 않는다")
    void unlock_KeepsLockedUntilConditionMet() {
        unlock();

        assertThat(memberCharacterRepository.findCharacterIdsByMemberId(me.getId()))
                .doesNotContain(MINT, SOLA);
    }

    /** 민트는 "종류와 관계없이 처음 완료" 라 활동일 하루면 열린다. */
    @Test
    @DisplayName("활동일이 차면 열리고 팝업이 뜬다")
    void unlock_OpensOnActiveDaysAndPublishesPopup() {
        memberActivityDayRepository.record(me.getId(), LocalDate.of(2026, 8, 13), false);
        unlock();

        assertAll(
                () -> assertThat(memberCharacterRepository.findCharacterIdsByMemberId(me.getId()))
                        .contains(MINT),
                () -> assertThat(popupQueryService.getPending(me.getId()).popups())
                        .anySatisfy(popup -> assertAll(
                                () -> assertThat(popup.type()).isEqualTo("CHARACTER_UNLOCKED"),
                                () -> assertThat(popup.referenceId()).isEqualTo(MINT))));
    }

    /** 판정은 인증이 들어올 때마다 돈다. 두 번째부터는 아무 일도 일어나지 않아야 한다. */
    @Test
    @DisplayName("다시 판정해도 두 번 열리지 않고 팝업도 하나뿐이다")
    void unlock_IsIdempotent() {
        memberActivityDayRepository.record(me.getId(), LocalDate.of(2026, 8, 13), false);
        unlock();
        unlock();

        assertAll(
                () -> assertThat(memberCharacterRepository.findCharacterIdsByMemberId(me.getId()))
                        .filteredOn(id -> id.equals(MINT)).hasSize(1),
                () -> assertThat(popupQueryService.getPending(me.getId()).popups())
                        .filteredOn(popup -> MINT == popup.referenceId()).hasSize(1));
    }

    /**
     * 캐릭터를 여는 경로가 둘이다 — 이 엔진(조건을 센다)과 업적 claim(직접 넣는다). 조건 표에
     * 두 종류가 섞여 있는데, 남의 키까지 AND 로 묶으면 이 경로가 영영 미달이 된다. 판정기가
     * 없으니 언제나 false 이고 오류도 로그도 없이 캐릭터가 안 열린다.
     */
    @Test
    @DisplayName("남의 경로가 건 조건은 이 엔진을 막지 않는다")
    void unlock_IgnoresConditionsOwnedByOtherMechanism() {
        em.createNativeQuery("""
                        insert into character_unlock_condition
                            (character_id, condition_key, condition_param, target_count,
                             sort_order, created_at, updated_at)
                        values (:characterId, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-001', 1, 9,
                                NOW(6), NOW(6))
                        """)
                .setParameter("characterId", MINT)
                .executeUpdate();
        memberActivityDayRepository.record(me.getId(), LocalDate.of(2026, 8, 13), false);

        unlock();

        assertThat(memberCharacterRepository.findCharacterIdsByMemberId(me.getId()))
                .as("활동일 조건을 채웠으면 열려야 한다").contains(MINT);
    }

    /** 셀 수 있는 조건이 하나도 없는 캐릭터를 "조건 없음" 으로 읽어 열어 주면 안 된다. */
    @Test
    @DisplayName("남의 조건만 걸린 캐릭터는 이 경로로 열지 않는다")
    void unlock_DoesNotOpenCharacterOwnedByOtherMechanism() {
        em.createNativeQuery("delete from character_unlock_condition where character_id = :characterId")
                .setParameter("characterId", NOA)
                .executeUpdate();
        em.createNativeQuery("""
                        insert into character_unlock_condition
                            (character_id, condition_key, condition_param, target_count,
                             sort_order, created_at, updated_at)
                        values (:characterId, 'ACHIEVEMENT_CLAIMED', 'ACH-EG-003', 1, 1,
                                NOW(6), NOW(6))
                        """)
                .setParameter("characterId", NOA)
                .executeUpdate();

        unlock();

        assertThat(memberCharacterRepository.findCharacterIdsByMemberId(me.getId()))
                .as("업적이 열 캐릭터를 기본 캐릭터로 오해하면 안 된다").doesNotContain(NOA);
    }

    /**
     * 솔라는 연속 100 일이다. 활동일이 100 개여도 <b>끊겨 있으면</b> 열리면 안 된다 —
     * ACTIVE_DAYS 와 STREAK_DAYS 를 같은 것으로 다루면 여기서 뚫린다.
     */
    @Test
    @DisplayName("연속 조건은 끊긴 활동일로 채워지지 않는다")
    void unlock_StreakIsNotCumulative() {
        LocalDate base = LocalDate.of(2026, 8, 13);
        for (int i = 0; i < 60; i++) {
            memberActivityDayRepository.record(me.getId(), base.minusDays(i), false);
        }
        // 하루 건너뛰고 다시 40 일 — 합치면 100 일이지만 연속은 60 일이다.
        for (int i = 61; i < 101; i++) {
            memberActivityDayRepository.record(me.getId(), base.minusDays(i), false);
        }
        unlock();

        assertAll(
                () -> assertThat(memberActivityDayRepository.countByMemberId(me.getId()))
                        .isEqualTo(100),
                () -> assertThat(memberCharacterRepository.findCharacterIdsByMemberId(me.getId()))
                        .as("누적은 100 이지만 연속은 60 이다").doesNotContain(SOLA));
    }
}
