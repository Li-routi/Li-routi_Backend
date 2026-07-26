package com.lirouti.global.util;

import java.time.ZoneId;

/**
 * 서비스의 날짜 기준 시간대.
 *
 * 스트릭·인증일·주기 판정은 모두 KST 자정을 기준으로 한다(database-schema.md).
 * 서버·DB의 시스템 시간대에 기대지 않도록, 날짜를 만들 때는 항상 이 상수를 넘긴다.
 */
public final class TimeUtil {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private TimeUtil() {
    }
}
