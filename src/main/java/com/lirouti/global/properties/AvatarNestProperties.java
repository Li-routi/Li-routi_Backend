package com.lirouti.global.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 둥지 자산과 레벨 기준.
 *
 * <p><b>둥지는 마스터 표가 없다.</b> 전 캐릭터·전 사용자가 같은 둥지를 쓰므로 행으로 둘 것이
 * 없고, 자산은 넉 장(레벨 둘 × 앞뒤)뿐이라 설정으로 충분하다.
 *
 * <p><b>레벨을 저장하지 않는다.</b> 강등이 있어서 지금 기록을 보고 계산하는 값이다 — 최근
 * {@code level2Days} 일 창에 "예정된 개인 루틴을 전부 완수한 날" 이 그만큼 들어 있으면 2 다.
 * 하루라도 비면 창에 그 수가 들어올 수 없어 <b>끊김을 따로 판정하지 않는다.</b>
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "avatar.nest")
public class AvatarNestProperties {

    /**
     * 레벨 2 에 필요한 연속 일수.
     *
     * <p>실사용 데이터가 없어 지금 이 숫자를 맞힐 방법이 없다. 강등이 있으므로 길수록 실수
     * 한 번이 비싸다 — 코드가 아니라 설정에 둔 이유다.
     *
     * <p><b>0 이하를 부팅에서 막는다.</b> 이 값은 창의 길이인 동시에 임계값이라, 0 이 들어오면
     * 창이 뒤집히고 "완수일이 0 개 이상" 이 되어 <b>전원이 레벨 2</b> 가 된다. 예외도 로그도
     * 없이 조용히 틀리므로, 뜨지 않는 편이 낫다.
     */
    @Positive(message = "둥지 레벨 2 일수는 양수여야 합니다.")
    private int level2Days = 15;

    /**
     * 둥지 자산 키 넷. <b>비면 이미지 주소가 깨진 채로 나간다.</b>
     *
     * <p>앱은 받은 주소를 그리기만 하므로 서버 로그에는 아무 오류도 남지 않는다 — 화면만
     * 깨지고 원인을 찾을 실마리가 없다. 그래서 빈 값을 부팅에서 막는다.
     *
     * <p>키를 바꾸면 <b>그 오브젝트가 버킷에 있어야 한다.</b> 시드가 참조하는 키가 아니라서
     * 마이그레이션과 함께 옮겨지지 않는다 — 개발 버킷에 없어서 둥지만 깨진 적이 있다.
     */
    @NotBlank(message = "둥지 자산 키는 비워 둘 수 없습니다.")
    private String level1BackKey = "avatar/nest/level1-back-v1.png";

    @NotBlank(message = "둥지 자산 키는 비워 둘 수 없습니다.")
    private String level1FrontKey = "avatar/nest/level1-front-v1.png";

    @NotBlank(message = "둥지 자산 키는 비워 둘 수 없습니다.")
    private String level2BackKey = "avatar/nest/level2-back-v1.png";

    @NotBlank(message = "둥지 자산 키는 비워 둘 수 없습니다.")
    private String level2FrontKey = "avatar/nest/level2-front-v1.png";
}
