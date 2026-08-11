package com.lirouti.global.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 포트원(PortOne) V2 결제 설정.
 *
 * <p>재화 충전의 결제를 포트원이 중개한다. 서버는 포트원에만 말하고, PG 사(토스페이먼츠)의
 * 키는 <b>포트원 콘솔에만</b> 있다 — 그래서 여기에 PG 키가 없다.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {

    /**
     * 결제 검증을 켤지.
     *
     * <p><b>끄면 충전이 막힌다.</b> AI 심사처럼 fail-open 으로 두지 않는다 — 심사는 못 하면
     * 통과시켜도 손해가 없지만, <b>결제 검증을 건너뛰고 지급하면 재화가 공짜가 된다.</b>
     * 이 값은 "검증을 안 한다" 가 아니라 "충전 기능을 내린다" 는 뜻이다.
     */
    private boolean enabled = true;

    /**
     * 포트원 V2 API Secret.
     *
     * <p><b>관리자 콘솔에서 발급한 V2 API Secret 이어야 한다.</b> PG 사에서 받은 시크릿 키를
     * 넣으면 모든 호출이 401 로 떨어진다.
     *
     * <p><b>{@code @NotBlank} 만으로는 미주입을 잡지 못한다.</b> 기본값 없이 선언해도 해석되지
     * 못한 플레이스홀더가 그 문자열 그대로 바인딩되어 검증을 통과한다. 실제로 AI 심사 키가
     * 그렇게 배포된 적이 있다 — 그래서 형식까지 본다.
     */
    @NotBlank(message = "포트원 API Secret 은 필수입니다. PORTONE_API_SECRET 환경변수를 주입하세요.")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]{20,}$",
            message = "포트원 API Secret 형식이 올바르지 않습니다. "
                    + "PORTONE_API_SECRET 이 주입되지 않았거나(플레이스홀더가 그대로 남았거나) 값이 잘못됐습니다."
    )
    private String apiSecret;

    /** 상점 아이디. 관리자 콘솔의 [결제 연동] → [연동 관리] 에서 확인한다. */
    @NotBlank(message = "포트원 상점 아이디는 필수입니다. PORTONE_STORE_ID 환경변수를 주입하세요.")
    private String storeId;

    /** API 서버 주소. 바꿀 일이 거의 없으므로 기본값을 둔다. */
    @NotBlank
    private String baseUrl = "https://api.portone.io";
}
