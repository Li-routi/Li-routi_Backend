package com.lirouti.domain.auth.service;

import com.lirouti.domain.auth.converter.AuthConverter;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.properties.JwtProperties;
import com.lirouti.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스웨거로 API를 확인할 때 쓸 토큰을 발급한다. <b>로컬에서만 존재한다.</b>
 *
 * 소셜 로그인은 카카오·구글 서버에 실제 토큰을 검증하러 가므로 로컬에서 임의 값으로는 통과하지
 * 못하고, 배포 서버에서 받은 토큰은 서명 키가 환경마다 달라 로컬에서 안 통한다. 액세스 토큰은
 * 만료가 5분이라 어렵게 받아도 금방 끊긴다. 그래서 로컬에는 토큰을 받을 방법이 사실상 없었다.
 *
 * <h3>조회 서비스가 아니다</h3>
 * 회원을 읽지만 <b>조회 유스케이스가 아니라 토큰 발급</b>이다. 읽는 이유는 발급 대상이 실제로
 * 있는지 확인하기 위해서다. CQRS를 적용하지 않는 {@code AuthService}·{@code TokenService}와
 * 같은 자리에 둔다(service_convention: 조회 기능이 없는 도메인은 CQRS를 강제하지 않는다).
 *
 * <h3>운영에 노출되지 않게 하는 방어가 둘이다</h3>
 * <ol>
 *   <li>{@code @Profile("local")} — 다른 프로파일에서는 빈이 등록되지 않는다.</li>
 *   <li>{@code bootJar}가 이 클래스를 배포 산출물에서 뺀다(build.gradle).</li>
 * </ol>
 *
 * 둘째가 필요한 이유는 <b>기본 활성 프로파일이 {@code local}</b>이기 때문이다
 * ({@code spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}}). 운영이 프로파일 주입에
 * 실패하면 그대로 {@code local}로 뜨고, 그 순간 {@code @Profile}은 아무것도 막지 못한다.
 * 클래스 자체가 이미지에 없으면 프로파일을 잘못 줘도 등록할 것이 없다 —
 * {@code db/dummy}를 배포 산출물에서 빼는 것과 같은 접근이다.
 */
@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class DevTokenService {
    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    /**
     * 개발용 토큰을 발급한다. 만료는 {@code jwt.dev-token.expiration-time}(14일)이다.
     *
     * <p>없는 회원이나 탈퇴한 회원이면 발급하지 않고 404로 막는다. 발급해도 토큰 자체는
     * 유효해서 호출하는 API마다 제각각 다른 실패를 내는데, 그러면 토큰이 잘못된 것인지
     * API가 잘못된 것인지 가리는 데 시간을 쓰게 된다.
     *
     * <p>역할은 항상 {@code ROLE_USER}다({@link JwtUtil#createDevToken}). 지금은 역할로
     * 갈리는 경로가 없어 문제되지 않지만, 관리자 전용 API가 생기면 이 토큰으로는 확인할 수 없다.
     */
    @Transactional(readOnly = true)
    public AuthResDTO.DevToken issue(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .filter(Member::isActiveMember)
                .orElseThrow(() -> new AuthException(AuthErrorCode.DEV_TOKEN_MEMBER_NOT_FOUND));

        log.warn("개발용 토큰을 발급했습니다. 로컬 전용입니다. memberId={}, nickname={}",
                member.getId(), member.getNickname());

        return AuthConverter.toDevToken(
                jwtUtil.createDevToken(member.getId()),
                jwtProperties.getDevToken().getExpirationTime()
        );
    }
}
