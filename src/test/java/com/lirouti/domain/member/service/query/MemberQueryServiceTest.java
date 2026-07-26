package com.lirouti.domain.member.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberQueryService 테스트")
class MemberQueryServiceTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private Member member;

    @InjectMocks
    private MemberQueryService memberQueryService;

    @Test
    @DisplayName("활성 회원을 조회하면 해당 회원을 반환한다")
    void getActiveMember_ActiveMember_ReturnsMember() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(member.getDeletedAt()).thenReturn(null);

        // when
        Member result = memberQueryService.getActiveMember(MEMBER_ID);

        // then
        assertThat(result).isSameAs(member);
    }

    @Test
    @DisplayName("회원 ID가 없으면 MEMBER_NOT_FOUND를 던진다")
    void getActiveMember_NullMemberId_ThrowsMemberNotFound() {
        // when & then
        assertThatThrownBy(() -> memberQueryService.getActiveMember(null))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 MEMBER_NOT_FOUND를 던진다")
    void getActiveMember_MemberNotFound_ThrowsMemberNotFound() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberQueryService.getActiveMember(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 회원이면 WITHDRAWN_MEMBER를 던진다")
    void getActiveMember_InactiveMember_ThrowsWithdrawnMember() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> memberQueryService.getActiveMember(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
    }

    @Test
    @DisplayName("탈퇴 일시가 기록된 회원이면 WITHDRAWN_MEMBER를 던진다")
    void getActiveMember_DeletedMember_ThrowsWithdrawnMember() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(member.getDeletedAt()).thenReturn(LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> memberQueryService.getActiveMember(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
    }
}
