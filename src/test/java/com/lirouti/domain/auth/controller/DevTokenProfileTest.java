package com.lirouti.domain.auth.controller;

import com.lirouti.domain.auth.service.DevTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 개발용 토큰 발급이 <b>local 이 아닌 곳에서는 존재하지 않는지</b> 확인한다.
 *
 * 이 검사가 필요한 이유는 스웨거가 공개 경로이기 때문이다. 이 엔드포인트가 열려 있으면
 * 주소를 아는 사람이 임의 회원의 토큰을 받아 그 회원으로 API 를 전부 호출할 수 있다.
 *
 * <p>테스트 컨텍스트는 {@code test} 프로파일로 뜬다(build.gradle). 즉 이 테스트는
 * "local 이 아닌 프로파일" 하나를 대표해서 검증한다.
 *
 * <p><b>이것만으로는 부족하다.</b> 활성 프로파일의 기본값이 {@code local} 이라
 * ({@code SPRING_PROFILES_ACTIVE:local}) 운영이 프로파일 주입에 실패하면 그대로 local 로 뜨고,
 * 그때는 {@code @Profile} 이 아무것도 막지 못한다. 그 경우를 막는 것은 build.gradle 의
 * bootJar 제외이며, 그쪽은 배포 산출물을 봐야 하므로 테스트로 확인하지 않는다.
 */
@SpringBootTest
@DisplayName("개발용 토큰 발급은 local 전용이다")
class DevTokenProfileTest {
    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("test 프로파일에서는 컨트롤러도 서비스도 등록되지 않는다")
    void devTokenBeans_NotLocalProfile_AreNotRegistered() {
        // when
        String[] controllers = context.getBeanNamesForType(DevTokenController.class);
        String[] services = context.getBeanNamesForType(DevTokenService.class);

        // then
        assertAll(
                () -> assertEquals(0, controllers.length,
                        "local 이 아닌 프로파일에 개발용 토큰 컨트롤러가 등록되었습니다."),
                () -> assertEquals(0, services.length,
                        "local 이 아닌 프로파일에 개발용 토큰 서비스가 등록되었습니다.")
        );
    }
}
