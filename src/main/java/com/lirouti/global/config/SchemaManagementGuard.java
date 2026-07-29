package com.lirouti.global.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 운영에서 {@code ddl-auto} 우회가 방치되지 않도록 기동 시 경고를 남긴다(#49).
 *
 * Flyway 도입(#46) 이후 운영의 기본값은 {@code validate}다. 다만 마이그레이션 문제로 부팅이
 * 막히면 배포가 통째로 멈추므로 {@code JPA_DDL_AUTO}로 한시 우회할 수 있게 열어 뒀다.
 * 문제는 그 우회가 조용하다는 것이다 — 한 번 넣고 잊으면 Hibernate가 스키마를 임의로 고치는 채로
 * 운영이 계속 돌고, Flyway를 넣은 목적(스키마 어긋남을 부팅 때 잡는다)이 사라진다.
 *
 * <b>우회를 막지는 않는다.</b> develop 머지가 곧 배포인 구조에서 긴급 우회 경로를 없애는 쪽이
 * 더 위험하다고 봤다. 목적은 "우회를 못 하게"가 아니라 "우회가 방치되지 않게"다.
 *
 * 배포 워크플로에도 같은 검사가 있지만(#49) 그쪽은 {@code ENV_FILE} 시크릿을 본다.
 * <b>서버 {@code /opt/app/.env}를 직접 고친 경우는 워크플로가 알 수 없고 이 로그만 잡아낸다.</b>
 * (직접 고친 값은 다음 배포에서 원복되지만, 그때까지는 그 값으로 돈다.)
 */
@Slf4j
@Component
@Profile("prod")
public class SchemaManagementGuard {
    private static final String EXPECTED = "validate";

    private final String ddlAuto;

    public SchemaManagementGuard(@Value("${spring.jpa.hibernate.ddl-auto:}") String ddlAuto) {
        this.ddlAuto = ddlAuto;
    }

    /**
     * 기동 완료 후에 찍는다. 시작 로그 한가운데 묻히지 않도록 마지막에 남긴다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfSchemaNotValidated() {
        if (!isOverridden(ddlAuto)) {
            return;
        }
        log.warn("""
                ⚠️ 운영인데 ddl-auto={} 입니다(기대값 {}). \
                Hibernate가 스키마를 직접 고치고 있어 Flyway 검증이 꺼진 상태입니다. \
                마이그레이션을 고친 뒤 ENV_FILE 시크릿에서 JPA_DDL_AUTO 줄을 지우고 재배포하세요.""",
                ddlAuto, EXPECTED);
    }

    /**
     * 값이 비어 있으면 우회가 아니다 — 프로퍼티 미주입은 {@code application.yaml}의 기본값
     * {@code validate}가 적용된 경우이므로 경고할 일이 없다.
     */
    static boolean isOverridden(String ddlAuto) {
        if (ddlAuto == null || ddlAuto.isBlank()) {
            return false;
        }
        return !EXPECTED.equals(ddlAuto.trim().toLowerCase(Locale.ROOT));
    }
}
