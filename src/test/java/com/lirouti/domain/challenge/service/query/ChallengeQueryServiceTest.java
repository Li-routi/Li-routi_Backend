package com.lirouti.domain.challenge.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChallengeQueryService 테스트")
class ChallengeQueryServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private MemberChallengeRepository memberChallengeRepository;

    @InjectMocks
    private ChallengeQueryService challengeQueryService;

    @BeforeEach
    void stubBatchCounts() {
        // 전체 목록은 카드 통계를 배치 집계한다. 대부분의 테스트는 통계 값 자체를 검증하지 않으므로 빈 맵으로 둔다.
        lenient().when(challengeRepository.countActiveParticipantsByChallengeIds(anyList())).thenReturn(Map.of());
        lenient().when(challengeRepository.countVerificationPostsByChallengeIds(anyList())).thenReturn(Map.of());
    }

    private Challenge challenge(String name) {
        return Challenge.builder()
                .name(name).description("설명").imageUrl("https://img/x.jpg")
                .category(ChallengeCategory.HEALTH).active(true).build();
    }

    // id는 auto-increment라 커서(nextCursor) 계산에 쓰인다. 테스트에선 리플렉션으로 채운다.
    private Challenge challengeWithId(long id) {
        Challenge c = challenge("c" + id);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    // id를 100,99,98…로 내림차순 부여(최신순 반환을 흉내). 마지막(가장 작은 id)이 nextCursor 후보.
    private List<Challenge> nChallenges(int n) {
        return IntStream.range(0, n).mapToObj(i -> challengeWithId(100L - i)).toList();
    }

    @Test
    @DisplayName("커서·크기를 지정하지 않으면 첫 페이지(cursor=null)·기본크기(20)로 조회한다")
    void getChallenges_Defaults() {
        when(challengeRepository.findByCursor(any(), any(), isNull(), anyInt()))
                .thenReturn(List.of());

        challengeQueryService.getChallenges(null, null, null, null);

        ArgumentCaptor<Long> cursorCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(challengeRepository).findByCursor(any(), any(), cursorCap.capture(), limitCap.capture());

        assertThat(cursorCap.getValue()).isNull();          // 첫 페이지
        assertThat(limitCap.getValue()).isEqualTo(21);       // size 20 + 1 (hasNext 판단용)
    }

    @Test
    @DisplayName("size+1건이 오면 hasNext=true, 초과분은 잘라내고 nextCursor는 남은 마지막 id다")
    void getChallenges_HasNextTrue_WhenExtraRow() {
        // size=2 요청 → 3건(id 100,99,98) 반환되면 다음 페이지 있음. 남는 2건은 100,99이고 nextCursor=99.
        when(challengeRepository.findByCursor(any(), any(), any(), eq(3)))
                .thenReturn(nChallenges(3));

        ChallengeResDTO.Listing result =
                challengeQueryService.getChallenges(null, null, null, 2);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.challenges()).hasSize(2);   // 초과분 잘림
        assertThat(result.nextCursor()).isEqualTo(99L);
    }

    @Test
    @DisplayName("size 이하로 오면 hasNext=false, nextCursor는 null")
    void getChallenges_HasNextFalse_WhenNotFull() {
        when(challengeRepository.findByCursor(any(), any(), any(), eq(3)))
                .thenReturn(nChallenges(2));

        ChallengeResDTO.Listing result =
                challengeQueryService.getChallenges(null, null, null, 2);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.challenges()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("cursor는 그대로 전달하고, size는 최대 50으로 제한한다")
    void getChallenges_PassesCursor_ClampsSize() {
        when(challengeRepository.findByCursor(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        challengeQueryService.getChallenges(null, null, 42L, 999);

        ArgumentCaptor<Long> cursorCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        verify(challengeRepository).findByCursor(any(), any(), cursorCap.capture(), limitCap.capture());

        assertThat(cursorCap.getValue()).isEqualTo(42L);  // 커서 그대로
        assertThat(limitCap.getValue()).isEqualTo(51);    // size 999 → 50, +1
    }

    @Test
    @DisplayName("전체 목록 카드에 배치 집계한 참여자 수·인증 게시글 수가 채워진다")
    void getChallenges_FillsCardCounts() {
        when(challengeRepository.findByCursor(any(), any(), any(), anyInt()))
                .thenReturn(List.of(challengeWithId(10L)));
        when(challengeRepository.countActiveParticipantsByChallengeIds(anyList()))
                .thenReturn(Map.of(10L, 7L));
        when(challengeRepository.countVerificationPostsByChallengeIds(anyList()))
                .thenReturn(Map.of(10L, 42L));

        ChallengeResDTO.Listing result = challengeQueryService.getChallenges(null, null, null, 20);

        assertThat(result.challenges()).hasSize(1);
        assertThat(result.challenges().get(0).participantCount()).isEqualTo(7L);
        assertThat(result.challenges().get(0).verificationPostCount()).isEqualTo(42L);
    }

    @Test
    @DisplayName("집계에 없는 챌린지는 참여자 수·인증 게시글 수가 0이다")
    void getChallenges_ZeroWhenNotAggregated() {
        when(challengeRepository.findByCursor(any(), any(), any(), anyInt()))
                .thenReturn(List.of(challengeWithId(10L)));
        // 배치 집계 맵이 비어 있음(참여·인증 없음) → 0으로 채워져야 한다.

        ChallengeResDTO.Listing result = challengeQueryService.getChallenges(null, null, null, 20);

        assertThat(result.challenges().get(0).participantCount()).isZero();
        assertThat(result.challenges().get(0).verificationPostCount()).isZero();
    }

    @Test
    @DisplayName("내 참여 목록은 회원의 활성 참여 챌린지를 심플 카드로 돌려준다")
    void getMyChallenges_ReturnsSimpleCards() {
        when(memberChallengeRepository.findMyActiveChallenges(eq(1L), isNull(), isNull()))
                .thenReturn(List.of(challenge("내 챌린지")));

        ChallengeResDTO.MyListing result = challengeQueryService.getMyChallenges(1L, null, null);

        assertThat(result.challenges()).hasSize(1);
        assertThat(result.challenges().get(0).name()).isEqualTo("내 챌린지");
    }

    @Test
    @DisplayName("상세 조회 시 참여자 수·인증 게시글 수·오늘 완료자 수를 함께 담는다")
    void getChallenge_ReturnsDetailWithCounts() {
        when(challengeRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(challenge("물 1L 마시기")));
        when(challengeRepository.countActiveParticipants(1L)).thenReturn(1234L);
        when(challengeRepository.countVerificationPosts(1L)).thenReturn(80L);
        when(challengeRepository.countTodayCompletions(eq(1L), any(LocalDate.class))).thenReturn(456L);

        // 비로그인 조회(memberId == null) → participating은 false.
        ChallengeResDTO.Detail result = challengeQueryService.getChallenge(1L, null);

        assertThat(result.participantCount()).isEqualTo(1234L);
        assertThat(result.verificationPostCount()).isEqualTo(80L);
        assertThat(result.todayCompletionCount()).isEqualTo(456L);
        assertThat(result.participating()).isFalse();
        assertThat(result.imageUrl()).isEqualTo("https://img/x.jpg");
    }

    @Test
    @DisplayName("로그인 조회 시 참여 중이면 participating=true")
    void getChallenge_LoggedInParticipating_ReturnsTrue() {
        when(challengeRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(challenge("물 1L 마시기")));
        when(challengeRepository.countActiveParticipants(1L)).thenReturn(0L);
        when(challengeRepository.countVerificationPosts(1L)).thenReturn(0L);
        when(challengeRepository.countTodayCompletions(eq(1L), any(LocalDate.class))).thenReturn(0L);
        MemberChallenge participating = MemberChallenge.builder()
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        when(memberChallengeRepository.findByMemberIdAndChallengeId(7L, 1L))
                .thenReturn(Optional.of(participating));

        ChallengeResDTO.Detail result = challengeQueryService.getChallenge(1L, 7L);

        assertThat(result.participating()).isTrue();
    }

    @Test
    @DisplayName("로그인 조회여도 그만둔 참여면 participating=false")
    void getChallenge_LoggedInButLeft_ReturnsFalse() {
        when(challengeRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(challenge("물 1L 마시기")));
        when(challengeRepository.countActiveParticipants(1L)).thenReturn(0L);
        when(challengeRepository.countVerificationPosts(1L)).thenReturn(0L);
        when(challengeRepository.countTodayCompletions(eq(1L), any(LocalDate.class))).thenReturn(0L);
        MemberChallenge left = MemberChallenge.builder()
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(false).build();
        when(memberChallengeRepository.findByMemberIdAndChallengeId(7L, 1L))
                .thenReturn(Optional.of(left));

        ChallengeResDTO.Detail result = challengeQueryService.getChallenge(1L, 7L);

        assertThat(result.participating()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않거나 비활성 챌린지면 예외를 던진다")
    void getChallenge_NotFound_ThrowsException() {
        when(challengeRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> challengeQueryService.getChallenge(99L, null))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }
}
