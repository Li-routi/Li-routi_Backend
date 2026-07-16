package com.lirouti.domain.challenge.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.ChallengeSortType;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.ChallengeSummaryProjection;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChallengeQueryService 테스트")
class ChallengeQueryServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @InjectMocks
    private ChallengeQueryService challengeQueryService;

    @Test
    @DisplayName("정렬을 지정하지 않으면 POPULAR로 조회한다")
    void getChallenges_NullSort_DefaultsToPopular() {
        // given
        when(challengeRepository.findSummaries(any(), any(), any())).thenReturn(List.of());

        // when
        challengeQueryService.getChallenges(null, null, null);

        // then
        ArgumentCaptor<ChallengeSortType> sortCaptor = ArgumentCaptor.forClass(ChallengeSortType.class);
        verify(challengeRepository).findSummaries(any(), any(), sortCaptor.capture());
        assertThat(sortCaptor.getValue()).isEqualTo(ChallengeSortType.POPULAR);
    }

    @Test
    @DisplayName("목록 조회 결과를 Listing(래퍼)으로 변환해 반환한다")
    void getChallenges_ReturnsListing() {
        // given
        Challenge challenge = Challenge.builder()
                .name("물 1L 마시기").description("설명").category(ChallengeCategory.HEALTH).active(true)
                .build();
        when(challengeRepository.findSummaries(any(), any(), any()))
                .thenReturn(List.of(new ChallengeSummaryProjection(challenge, 1234L)));

        // when
        ChallengeResDTO.Listing result =
                challengeQueryService.getChallenges(ChallengeCategory.HEALTH, "물", ChallengeSortType.POPULAR);

        // then
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.challenges()).hasSize(1);
        assertThat(result.challenges().get(0).name()).isEqualTo("물 1L 마시기");
        assertThat(result.challenges().get(0).participantCount()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("상세 조회 시 참여자 수와 오늘 완료자 수를 함께 담는다")
    void getChallenge_ReturnsDetailWithCounts() {
        // given
        Challenge challenge = Challenge.builder()
                .name("물 1L 마시기").category(ChallengeCategory.HEALTH).active(true).build();
        when(challengeRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(challenge));
        when(challengeRepository.countActiveParticipants(1L)).thenReturn(1234L);
        when(challengeRepository.countTodayCompletions(eq(1L), any(LocalDate.class))).thenReturn(456L);

        // when
        ChallengeResDTO.Detail result = challengeQueryService.getChallenge(1L);

        // then
        assertThat(result.participantCount()).isEqualTo(1234L);
        assertThat(result.todayCompletionCount()).isEqualTo(456L);
    }

    @Test
    @DisplayName("존재하지 않거나 비활성 챌린지면 예외를 던진다")
    void getChallenge_NotFound_ThrowsException() {
        // given
        when(challengeRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> challengeQueryService.getChallenge(99L))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }
}
