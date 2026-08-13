package com.lirouti.domain.popup;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.popup.dto.response.PopupResDTO;
import com.lirouti.domain.popup.entity.PendingPopup;
import com.lirouti.domain.popup.exception.PopupException;
import com.lirouti.domain.popup.repository.PendingPopupRepository;
import com.lirouti.domain.popup.service.command.PopupCommandService;
import com.lirouti.domain.popup.service.command.PopupPublishCommand;
import com.lirouti.domain.popup.service.query.PopupQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 앱 진입 시 띄우는 팝업.
 *
 * <p>이 모듈의 계약은 셋이다 — <b>같은 사건은 한 번만</b>, <b>확인 전에는 계속 뜬다</b>,
 * <b>남의 것은 못 건드린다</b>. 셋 중 하나라도 뚫리면 사용자는 같은 팝업을 반복해서 보거나
 * 반대로 열린 줄도 모른 채 지나간다.
 */
@SpringBootTest
@Transactional
@DisplayName("팝업 모듈 테스트")
class PendingPopupTest {

    @Autowired
    private PopupCommandService popupCommandService;
    @Autowired
    private PopupQueryService popupQueryService;
    @Autowired
    private PendingPopupRepository pendingPopupRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private Member other;

    @BeforeEach
    void setUp() {
        me = persistMember();
        other = persistMember();
        em.flush();
    }

    private Member persistMember() {
        int n = seq.incrementAndGet();
        Member member = Member.builder()
                .email("popup" + n + "@ex.com").nickname("popup" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("popup-sid-" + n).build();
        em.persist(member);
        return member;
    }

    private PopupPublishCommand unlocked(Long memberId, long characterId) {
        return new PopupPublishCommand(
                memberId, "CHARACTER_UNLOCKED", "새 친구가 왔어요", "루티를 만났어요",
                "avatar/character/ROUTI/adult-v1.png", "CHARACTER", characterId,
                "character-unlocked:" + characterId);
    }

    // ── 발행 ──

    @Test
    @DisplayName("발행하면 안 본 목록에 뜬다")
    void publish_ShowsInPending() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        em.flush();

        PopupResDTO.Popups result = popupQueryService.getPending(me.getId());

        assertThat(result.popups()).singleElement().satisfies(popup -> assertAll(
                () -> assertThat(popup.type()).isEqualTo("CHARACTER_UNLOCKED"),
                () -> assertThat(popup.title()).isEqualTo("새 친구가 왔어요"),
                () -> assertThat(popup.referenceId()).isEqualTo(1L)));
    }

    /**
     * 판정이 여러 번 도는 것이 정상이다. 인증이 들어올 때마다 조건을 보므로, 같은 사건으로
     * 두 번 발행되면 사용자는 같은 팝업을 두 번 본다.
     */
    @Test
    @DisplayName("같은 사건을 다시 발행해도 하나뿐이다")
    void publish_IsIdempotentByDedupKey() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        popupCommandService.publish(unlocked(me.getId(), 1L));
        em.flush();

        assertThat(popupQueryService.getPending(me.getId()).popups()).hasSize(1);
    }

    @Test
    @DisplayName("dedup_key 가 다르면 따로 뜬다 — 캐릭터가 둘 열리면 둘 다 보여야 한다")
    void publish_DifferentEventsAreSeparate() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        popupCommandService.publish(unlocked(me.getId(), 2L));
        em.flush();

        assertThat(popupQueryService.getPending(me.getId()).popups())
                .extracting(PopupResDTO.Popup::referenceId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("같은 dedup_key 라도 회원이 다르면 각자 받는다")
    void publish_DedupIsPerMember() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        popupCommandService.publish(unlocked(other.getId(), 1L));
        em.flush();

        assertAll(
                () -> assertThat(popupQueryService.getPending(me.getId()).popups()).hasSize(1),
                () -> assertThat(popupQueryService.getPending(other.getId()).popups()).hasSize(1));
    }

    // ── 조회 ──

    @Test
    @DisplayName("이미지 key 는 주소로 조립해 내린다")
    void getPending_ResolvesImageUrl() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        em.flush();

        PopupResDTO.Popup popup = popupQueryService.getPending(me.getId()).popups().getFirst();

        assertAll(
                () -> assertThat(popup.imageUrl()).startsWith("http"),
                () -> assertThat(popup.imageUrl())
                        .endsWith("avatar/character/ROUTI/adult-v1.png"),
                () -> assertThat(popup.imageUrl())
                        .as("key 를 그대로 내리면 앱이 그리지 못한다")
                        .isNotEqualTo("avatar/character/ROUTI/adult-v1.png"));
    }

    /** 이미지 없는 팝업이 있을 수 있다. 없는 것을 조립하면 오리진만 남은 주소가 나간다. */
    @Test
    @DisplayName("이미지가 없으면 주소도 비운다")
    void getPending_KeepsNullImageNull() {
        popupCommandService.publish(new PopupPublishCommand(
                me.getId(), "ACHIEVEMENT_ACHIEVED", "업적 달성", "축하해요",
                null, null, null, "achievement-achieved:1"));
        em.flush();

        assertThat(popupQueryService.getPending(me.getId()).popups())
                .singleElement()
                .satisfies(popup -> assertThat(popup.imageUrl()).isNull());
    }

    @Test
    @DisplayName("남의 팝업은 내 목록에 없다")
    void getPending_IsolatedPerMember() {
        popupCommandService.publish(unlocked(other.getId(), 1L));
        em.flush();

        assertThat(popupQueryService.getPending(me.getId()).popups()).isEmpty();
    }

    // ── 확인 ──

    @Test
    @DisplayName("확인하면 다음 조회에서 빠진다")
    void ack_RemovesFromPending() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        em.flush();
        Long popupId = popupQueryService.getPending(me.getId()).popups().getFirst().id();

        popupCommandService.ack(me.getId(), List.of(popupId));
        em.flush();

        assertThat(popupQueryService.getPending(me.getId()).popups()).isEmpty();
    }

    /** 앱이 재시도하는 것이 정상 경로다. 오류로 만들면 화면이 흔들린다. */
    @Test
    @DisplayName("같은 것을 두 번 확인해도 성공이고 처음 본 시각은 그대로다")
    void ack_IsIdempotentAndKeepsFirstTime() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        em.flush();
        Long popupId = popupQueryService.getPending(me.getId()).popups().getFirst().id();

        popupCommandService.ack(me.getId(), List.of(popupId));
        em.flush();
        var firstAckedAt = pendingPopupRepository.findById(popupId).orElseThrow().getAckedAt();

        popupCommandService.ack(me.getId(), List.of(popupId));
        em.flush();

        assertThat(pendingPopupRepository.findById(popupId).orElseThrow().getAckedAt())
                .isEqualTo(firstAckedAt);
    }

    @Test
    @DisplayName("여러 건을 한 번에 확인한다")
    void ack_AcceptsMany() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        popupCommandService.publish(unlocked(me.getId(), 2L));
        em.flush();
        List<Long> ids = popupQueryService.getPending(me.getId()).popups().stream()
                .map(PopupResDTO.Popup::id).toList();

        popupCommandService.ack(me.getId(), ids);
        em.flush();

        assertThat(popupQueryService.getPending(me.getId()).popups()).isEmpty();
    }

    /**
     * 남의 것을 확인해 주면 그 사람은 열린 줄도 모르고 지나간다. 없는 id 와 가르지 않는 것은
     * 별개 이유다 — 가르면 id 를 넣어 보는 것만으로 남의 팝업이 존재하는지 알 수 있다.
     */
    @Test
    @DisplayName("남의 팝업은 확인할 수 없다")
    void ack_RejectsOthersPopup() {
        popupCommandService.publish(unlocked(other.getId(), 1L));
        em.flush();
        Long othersPopupId = pendingPopupRepository
                .findAllByMemberIdAndAckedAtIsNullOrderByCreatedAtAsc(other.getId())
                .getFirst().getId();

        assertThatThrownBy(() -> popupCommandService.ack(me.getId(), List.of(othersPopupId)))
                .isInstanceOf(PopupException.class);
    }

    @Test
    @DisplayName("하나라도 없는 id 면 전체를 거절한다 — 부분 성공은 없다")
    void ack_RejectsWholeRequestOnUnknownId() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        em.flush();
        Long mine = popupQueryService.getPending(me.getId()).popups().getFirst().id();

        assertThatThrownBy(() -> popupCommandService.ack(me.getId(), List.of(mine, 999_999L)))
                .isInstanceOf(PopupException.class);
    }

    // ── 저장 ──

    @Test
    @DisplayName("발행 직후에는 확인 전 상태다")
    void publish_StartsUnacked() {
        popupCommandService.publish(unlocked(me.getId(), 1L));
        em.flush();

        PendingPopup saved = pendingPopupRepository
                .findAllByMemberIdAndAckedAtIsNullOrderByCreatedAtAsc(me.getId()).getFirst();

        assertAll(
                () -> assertThat(saved.isAcked()).isFalse(),
                () -> assertThat(saved.getDedupKey()).isEqualTo("character-unlocked:1"));
    }
}
