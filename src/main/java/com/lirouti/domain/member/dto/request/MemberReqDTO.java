package com.lirouti.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MemberReqDTO {
    private MemberReqDTO() {
    }

    public record Withdraw(
            @NotBlank(message = "탈퇴 확인 문구는 필수입니다.")
            String confirmation
    ) {
    }

    public record UpdateProfile(
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 1, max = 10, message = "닉네임은 10자 이하여야 합니다.")
            String nickname
    ) {
        // 기능명세서 내 '프로필 조회와 닉네임 수정' 페이지에 명시된 닉네임 조건 : 앞 뒤 공백 제거 후 1 - 10자
        public UpdateProfile {
            if(nickname != null) {
                nickname = nickname.strip();
            }
        }
    }
}
