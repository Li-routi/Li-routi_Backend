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

                    reviewStatus 는 챌린지 인증에만 값이 있습니다. 루틴 인증은 심사가 없어 null 입니다.

                    reviewStatus 가 PENDING 이면 아직 공개되지 않은 인증입니다. 심사기가 답을 못 줘
                    보류 중이고 복구되면 다시 심사하며, 그동안 그 사진은 본인에게만 보입니다.
                    이때 imageUrl 은 공개 주소가 아니라 한시적 서명 주소이니, 오래 들고 있다가
                    쓰면 만료됩니다. 화면에는 "심사 중" 으로 표시해 주세요.

                    status=PENDING 으로 좁히면 심사 중인 챌린지 인증만 내려갑니다 —
                    루틴 인증은 심사가 없어 빠집니다.
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
