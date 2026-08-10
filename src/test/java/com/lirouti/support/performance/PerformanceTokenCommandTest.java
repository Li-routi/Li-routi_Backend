package com.lirouti.support.performance;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceTokenCommandTest {
    private static final Long MEMBER_ID = 9001L;

    @TempDir
    Path tempDirectory;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private Member member;

    @Test
    void resolveMemberId_MissingValue_ReturnsDefaultMemberId() {
        assertThat(PerformanceTokenCommand.resolveMemberId(null)).isEqualTo(MEMBER_ID);
        assertThat(PerformanceTokenCommand.resolveMemberId(" ")).isEqualTo(MEMBER_ID);
    }

    @Test
    void resolveMemberId_InvalidValue_ThrowsException() {
        assertThatThrownBy(() -> PerformanceTokenCommand.resolveMemberId("member"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId는 숫자여야 합니다.");

        assertThatThrownBy(() -> PerformanceTokenCommand.resolveMemberId("0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId는 양수여야 합니다.");
    }

    @Test
    void issue_ActiveMember_ReturnsDevToken() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.isActiveMember()).thenReturn(true);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(jwtUtil.createDevToken(MEMBER_ID)).thenReturn("dev-token");

        String token = PerformanceTokenCommand.issue(MEMBER_ID, memberRepository, jwtUtil);

        assertThat(token).isEqualTo("dev-token");
        verify(jwtUtil).createDevToken(MEMBER_ID);
    }

    @Test
    void issue_MemberNotFound_ThrowsMemberException() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> PerformanceTokenCommand.issue(MEMBER_ID, memberRepository, jwtUtil))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void issue_InactiveMember_ThrowsMemberException() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.isActiveMember()).thenReturn(false);

        assertThatThrownBy(() -> PerformanceTokenCommand.issue(MEMBER_ID, memberRepository, jwtUtil))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void writeUsersCsv_ReplacesExistingFile() throws Exception {
        Path output = tempDirectory.resolve("nested/users.csv");

        PerformanceTokenCommand.writeUsersCsv(output, "dev-token");
        PerformanceTokenCommand.writeUsersCsv(output, "new-token");

        assertThat(Files.readString(output, StandardCharsets.UTF_8))
                .isEqualTo("access_token\nnew-token\n");
    }
}
