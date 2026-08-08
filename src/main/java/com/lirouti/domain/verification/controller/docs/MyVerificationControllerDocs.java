package com.lirouti.domain.verification.controller.docs;

import com.lirouti.domain.verification.dto.response.MyVerificationResDTO;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.LocalDate;

@Tag(name = "마이 - 내 인증", description = "마이페이지 내 인증 화면(챌린지, 개인 루틴, 그룹 루틴 통합) API")
public interface MyVerificationControllerDocs {

    @Operation(
            summary = "내 인증 일별 조회",
            description = """
                    특정 날짜에 내가 남긴 인증을 챌린지·개인 루틴·그룹 루틴 구분 없이 한 목록으로
                    합쳐 최신순으로 내려줍니다. 마이 > 내인증 화면에서 날짜를 좌우로 넘길 때마다 호출합니다.

                    date를 생략하면 오늘(KST) 기준으로 조회합니다.

                    검수 상태(대기/승인/반려) 값은 아직 내려주지 않습니다 — 검수 파이프라인이
                    도입되면 그때 추가합니다. 지금 존재하는 인증은 전부 완료로 취급해 주세요.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공(기록이 없으면 빈 배열)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미로그인)")
    })
    ApiResponse<MyVerificationResDTO.DailyFeed> getMyVerifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 날짜(yyyy-MM-dd). 생략 시 오늘(KST)") LocalDate date,
            @Parameter(description = "심사 상태로 좁힌다. 생략하면 전부. "
                    + "PENDING 이면 심사 중인 챌린지 인증만 — 루틴 인증은 심사가 없어 빠진다")
            ReviewStatus status
    );
}
