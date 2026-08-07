package com.lirouti.domain.auth.controller;

import com.lirouti.domain.auth.controller.docs.DevTokenControllerDocs;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.code.success.AuthSuccessCode;
import com.lirouti.domain.auth.service.DevTokenService;
import com.lirouti.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬에서 스웨거로 API를 확인할 때 쓸 토큰을 발급한다.
 *
 * <h3>경로를 {@code /api/auth} 아래에 두지 않는다</h3>
 * {@code /api/auth/**}는 이미 공개 경로라 거기 두면 {@code SecurityConfig}를 고치지 않아도 된다.
 * 그런데 그러면 <b>개발용 우회 경로가 어디에도 드러나지 않는다.</b> 공개 목록에 한 줄로 남겨
 * 리뷰에서 눈에 걸리게 한다.
 *
 * <h3>회원 id를 쿼리 파라미터가 아니라 경로 변수로 받는다</h3>
 * 필수 {@code @RequestParam}을 빠뜨리면 {@code MissingServletRequestParameterException}이 나는데,
 * 전역 처리기에 이 예외의 핸들러가 없어 catch-all로 빠져 <b>500</b>이 된다. 경로 변수는 빠뜨리면
 * 매핑 자체가 안 잡혀 404이고, 숫자가 아니면 이미 400으로 변환되는 예외가 난다
 * ({@code MethodArgumentTypeMismatchException}). 토큰을 받으려다 500을 보면 서버가 고장난 줄 안다.
 *
 * <p>음수·0은 따로 막지 않는다. 그런 회원은 조회되지 않아 404로 걸리고, 문구도 그 상황에 맞다.
 *
 * <p>이 클래스가 운영에 없어야 하는 이유와 방어 수단은 {@link DevTokenService} 참고.
 */
@RestController
@Profile("local")
@RequiredArgsConstructor
@RequestMapping("/api/dev")
public class DevTokenController implements DevTokenControllerDocs {
    private final DevTokenService devTokenService;

    @Override
    @PostMapping("/token/{memberId}")
    public ApiResponse<AuthResDTO.DevToken> issueDevToken(@PathVariable Long memberId) {
        AuthResDTO.DevToken response = devTokenService.issue(memberId);
        return ApiResponse.onSuccess(AuthSuccessCode.DEV_TOKEN_ISSUE_SUCCESS, response);
    }
}
