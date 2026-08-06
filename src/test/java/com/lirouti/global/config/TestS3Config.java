package com.lirouti.global.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * 테스트에서 S3 를 실제로 부르지 않게 막는다.
 *
 * <h3>왜 필요한가</h3>
 * 인증 흐름이 심사를 통과한 사진을 대기 prefix 에서 공개 prefix 로 <b>복사</b>하면서, 통합
 * 테스트가 처음으로 S3 를 직접 부르게 됐다. 업로드 바이트 검증은 {@code byte-validation-enabled:
 * false} 로 꺼져 있어 지금까지는 S3 호출이 없었다.
 *
 * <p>테스트마다 {@code MediaService} 를 목으로 바꾸는 방법도 있지만, 그러면 <b>key 발급 규칙과
 * 검증까지 함께 사라진다.</b> 그 부분은 진짜 코드가 도는 편이 낫다 — 대기 prefix 로 받는지,
 * 잘못된 key 를 막는지가 이 기능의 핵심이기 때문이다. 그래서 더 아래인 S3 클라이언트만 끊는다.
 *
 * <h3>목이 무엇을 돌려주는가</h3>
 * Mockito 기본값(널 또는 빈 응답)이다. 복사·삭제는 반환값을 쓰지 않으므로 그대로 성공한 것이
 * 된다. <b>S3 가 실패하는 경우를 보고 싶은 테스트는 이 목을 직접 스텁해야 한다.</b>
 *
 * <p><b>{@code @TestConfiguration} 이 아니라 {@code @Configuration} 이다.</b> 전자는 명시적으로
 * import 해야 올라오므로 테스트마다 한 줄씩 붙여야 한다. 이 설정은 S3 를 부르는 모든 통합
 * 테스트에 예외 없이 적용돼야 해서, 컴포넌트 스캔에 잡히는 쪽을 택했다.
 *
 * <p>바이트를 읽는 경로({@code GetObject})는 여기서 다루지 않는다. 그쪽은 설정으로 꺼져 있고,
 * 켜서 보는 테스트는 {@code MediaServiceTest} 처럼 자기 목을 따로 만든다.
 */
@Configuration
@Profile("test")
public class TestS3Config {

    /**
     * <b>메서드 이름을 {@code s3Client} 로 두면 안 된다.</b> 빈 이름이 {@link S3Config} 의 것과
     * 같아져 정의 충돌로 컨텍스트가 아예 뜨지 않는다(빈 정의 덮어쓰기는 기본이 꺼져 있다).
     * 이름을 달리하고 {@code @Primary} 로 주입 우선순위만 가져온다.
     */
    @Bean
    @Primary
    public S3Client testS3Client() {
        S3Client mock = Mockito.mock(S3Client.class);

        // 읽기는 "S3 를 못 쓴다"로 둔다. 목의 기본값(널)을 그대로 두면 호출부가 응답을 뜯다가
        // NPE 로 죽는데, 그건 실제로 일어날 수 없는 모양이다 — 진짜 클라이언트는 실패할 때
        // 예외를 던진다. 이 프로젝트의 읽기 경로는 그 예외를 잡아 "심사 건너뜀"으로 처리하므로,
        // 예외를 던지게 해야 테스트가 운영과 같은 갈래를 탄다.
        Mockito.when(mock.getObject(Mockito.any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("테스트에서는 S3 를 읽지 않습니다."));

        // 쓰기(복사·삭제)는 성공으로 둔다. 반환값을 쓰지 않으므로 기본값이면 충분하다.
        return mock;
    }
}
