package com.lirouti.domain.challenge.service;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미참조 이미지 정리가 무엇을 "쓰이는 중"으로 볼지 결정하는 쪽.
 *
 * 여기서 빠뜨린 key는 배치가 <b>지운다.</b> 그래서 조회 조건이 좁혀지지 않았는지를 본다 —
 * 탈퇴·비활성처럼 "화면에서 안 보이는" 상태가 "파일을 지워도 되는" 상태로 오인되면 안 된다.
 */
@SpringBootTest
@Transactional
@DisplayName("ChallengeMediaReferenceSource 미디어 참조 조회 테스트")
class ChallengeMediaReferenceSourceTest {

    @Autowired
    private ChallengeMediaReferenceSource referenceSource;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    // ── 헬퍼 ──
    private Member member(boolean active) {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("mref-m" + n + "@ex.com").nickname("nick" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mref-sid-" + n).build();
        if (!active) {
            ReflectionTestUtils.setField(m, "isActive", false);
        }
        em.persist(m);
        return m;
    }

    private Challenge challenge(boolean active, String imageUrl) {
        Challenge c = Challenge.builder()
                .name("mref챌린지" + seq.incrementAndGet()).category(ChallengeCategory.HEALTH)
                .active(active).imageUrl(imageUrl).build();
        em.persist(c);
        return c;
    }

    private ChallengeVerification verify(Member m, Challenge c, String imageUrl) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc)
                .participationRound(1)
                .verifiedDate(LocalDate.of(2026, 7, 23).plusDays(seq.incrementAndGet()))
                .verifiedAt(LocalDateTime.of(2026, 7, 23, 9, 0))
                .imageUrl(imageUrl)
                .content("인증")
                .build();
        em.persist(v);
        return v;
    }

    // ── 테스트 ──
    @Test
    @DisplayName("인증 사진으로 쓰이는 key만 골라낸다")
    void findReferencedKeys_OnlyUsedKeys_AreReturned() {
        // given
        String used = "challenge-verifications/2026/07/23/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg";
        String unused = "challenge-verifications/2026/07/23/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg";
        verify(member(true), challenge(true, null), used);
        em.flush();

        // when
        Set<String> referenced = referenceSource.findReferencedKeys(List.of(used, unused));

        // then
        assertThat(referenced).containsExactly(used);
    }

    @Test
    @DisplayName("탈퇴 회원의 인증 사진도 참조로 친다 — 행이 남아 있으면 파일도 살아 있다")
    void findReferencedKeys_WithdrawnMember_IsStillReferenced() {
        // given: 피드에서는 빠지지만 파일은 지우면 안 된다(탈퇴 시 삭제는 별도 정책)
        String key = "challenge-verifications/2026/07/23/cccccccc-cccc-cccc-cccc-cccccccccccc.jpg";
        verify(member(false), challenge(true, null), key);
        em.flush();

        // when
        Set<String> referenced = referenceSource.findReferencedKeys(List.of(key));

        // then
        assertThat(referenced).containsExactly(key);
    }

    @Test
    @DisplayName("챌린지 대표 이미지도 참조로 친다 — 비활성 챌린지까지 포함")
    void findReferencedKeys_ChallengeThumbnail_IsReferenced() {
        // given: 지금은 항상 비어 있지만, 채워졌을 때 정리 배치가 지우면 안 된다
        String key = "challenge-verifications/2026/07/23/dddddddd-dddd-dddd-dddd-dddddddddddd.jpg";
        challenge(false, key);
        em.flush();

        // when
        Set<String> referenced = referenceSource.findReferencedKeys(List.of(key));

        // then
        assertThat(referenced).containsExactly(key);
    }

    @Test
    @DisplayName("후보가 비면 질의하지 않고 빈 결과를 준다")
    void findReferencedKeys_EmptyCandidates_ReturnsEmpty() {
        // when
        Set<String> referenced = referenceSource.findReferencedKeys(List.of());

        // then
        assertThat(referenced).isEmpty();
    }

    @Test
    @DisplayName("챌린지 인증 용도만 담당한다 — 프로필은 담당자가 없어 정리 대상이 아니다")
    void coveredPurposes_OnlyChallengeVerification() {
        // when & then
        assertThat(referenceSource.coveredPurposes())
                .containsExactly(MediaPurpose.CHALLENGE_VERIFICATION);
    }
}
