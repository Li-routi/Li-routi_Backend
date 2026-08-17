package com.lirouti.domain.media.service;

import com.lirouti.domain.media.enums.MediaContentType;
import com.lirouti.domain.media.enums.MediaPurpose;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S3 미디어 key의 형식 규칙. <b>발급·검증·정리가 모두 이 한 곳을 본다.</b>
 *
 * <p>규칙을 여러 곳에 복사해 두면 한쪽만 고쳐져 갈라진다. 마침 이 저장소에서 그게
 * 실제로 사고를 낸 적이 있다 — 마이그레이션 중복 검사 로직이 Taskfile과 CI에 두 벌로
 * 있었고, 정작 필요한 자리에서는 아무도 보지 않았다.
 *
 * <p>정리 배치가 "무엇을 지워도 되는가"를 이 규칙으로 판단하므로, 여기가 틀리면
 * 살아 있는 파일을 지우거나 쓰레기를 영원히 남긴다.
 */
final class MediaKeyFormat {

    /**
     * key의 날짜 구간. 스트릭·인증일과 같은 KST 기준을 쓴다.
     *
     * STRICT + uuuu 조합인 이유: 2026/02/30 · 2025/13/99 처럼 달력에 없는 날짜를 걸러내야 한다.
     * 기본(SMART) 해석은 일자를 그 달의 마지막 날로 맞춰버려 통과시킨다.
     * STRICT 에서는 연도 패턴이 yyyy(era 기준)면 era 없이 파싱할 수 없어 uuuu 를 쓴다.
     */
    static final DateTimeFormatter KEY_DATE_PATH = DateTimeFormatter
            .ofPattern("uuuu/MM/dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** 리프 파일명. {@code randomUUID()}가 만드는 소문자 16진수로 고정한다. */
    private static final String UUID_LEAF =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /**
     * 용도 prefix 뒤에 올 수 있는 부분의 형태.
     *
     * {@code (yyyy/MM/dd/)?UUID} 두 가지를 모두 받는다. 날짜 도입 전에 발급된
     * {@code prefix/UUID} 형태가 이미 저장돼 있고, 형식이 바뀌기 전에 발급된 key가 바뀐 뒤에
     * 저장될 수도 있다(presigned URL 유효 시간이 5분이라 창은 좁다).
     */
    private static final Pattern ISSUED_KEY_BODY =
            Pattern.compile("(?<date>\\d{4}/\\d{2}/\\d{2}/)?" + UUID_LEAF);

    private static final Pattern ISSUED_LEAF = Pattern.compile(UUID_LEAF);

    private MediaKeyFormat() {
    }

    /**
     * 용도 prefix를 뗀 나머지가 발급 형식({@code (날짜/)?UUID})인지, 날짜가 실제 달력에
     * 있는 날인지까지 확인한다.
     */
    static boolean isIssuedBody(String baseName) {
        Matcher matcher = ISSUED_KEY_BODY.matcher(baseName);
        return matcher.matches() && isValidDatePathOrAbsent(matcher.group("date"));
    }

    /**
     * 리프 파일명만 본다. 정리 배치는 날짜 prefix로 목록을 뽑으므로 날짜가 이미 확정돼 있어
     * 다시 검사할 필요가 없다.
     */
    static boolean isIssuedLeaf(String baseName) {
        return ISSUED_LEAF.matcher(baseName).matches();
    }

    /** 그 용도가 허용하는 카테고리의 형식들만 확장자로 인정한다(사진 전용 용도에 mp4 key 방지). */
    static boolean isAllowedExtension(MediaPurpose purpose, String extension) {
        return Arrays.stream(MediaContentType.values())
                .filter(type -> purpose.allows(type.getCategory()))
                .anyMatch(type -> type.getExtension().equals(extension));
    }

    /**
     * 날짜 구간이 달력에 실제로 있는 날인지 본다.
     *
     * 정규식은 자릿수만 보므로 2026/02/30 · 2025/13/99 도 통과한다. 그런 key는 서버가 발급한
     * 적이 없으니 보통 업로드 바이트 확인에서 걸리지만, 그 검증은 킬 스위치로 끌 수 있다
     * (AWS_S3_BYTE_VALIDATION_ENABLED). 꺼진 동안에는 존재하지 않는 오브젝트를 가리키는 key가
     * 그대로 저장되어 피드에 깨진 이미지로 남는다.
     *
     * 날짜가 없는 형태(날짜 도입 전 발급)는 통과시킨다.
     */
    private static boolean isValidDatePathOrAbsent(String datePathWithSlash) {
        if (datePathWithSlash == null) {
            return true;
        }
        try {
            // 정규식이 잡은 구간은 끝에 '/'가 붙어 있다.
            LocalDate.parse(datePathWithSlash.substring(0, datePathWithSlash.length() - 1), KEY_DATE_PATH);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
