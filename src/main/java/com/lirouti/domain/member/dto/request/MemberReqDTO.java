package com.lirouti.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;

public final class MemberReqDTO {
    private MemberReqDTO() {
    }

    public record Withdraw(
            @NotBlank(message = "탈퇴 확인 문구는 필수입니다.")
            String confirmation
    ) {
    }
}
