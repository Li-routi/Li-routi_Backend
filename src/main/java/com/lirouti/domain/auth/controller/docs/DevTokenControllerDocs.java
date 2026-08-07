package com.lirouti.domain.auth.controller.docs;

import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Dev", description = "로컬 개발용 API — 운영에는 존재하지 않습니다")
public interface DevTokenControllerDocs {

    @Operation(
            summary = "개발용 토큰 발급 (로컬 전용)",
            description = """
                    스웨거에서 인증이 필요한 API를 확인할 때 쓸 액세스 토큰을 발급합니다.
                    받은 accessToken 을 우측 상단 Authorize 에 넣으면 됩니다.

                    로컬 더미 회원은 9001, 9002 입니다(db/dummy).
                    만료는 14일이라 한 번 받아두면 한동안 다시 받지 않아도 됩니다.

                    없는 회원이거나 탈퇴한 회원이면 404 로 막습니다.

                    이 API 는 local 프로파일에서만 존재하며 배포 산출물에는 포함되지 않습니다.
                    """
    )
    ApiResponse<AuthResDTO.DevToken> issueDevToken(
            @Parameter(description = "토큰을 발급받을 회원 id", example = "9001")
            Long memberId
    );
}
