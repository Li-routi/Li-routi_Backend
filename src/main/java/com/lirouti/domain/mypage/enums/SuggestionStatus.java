package com.lirouti.domain.mypage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 건의 처리 상태.
 *
 * <p><b>지금은 바꾸는 경로가 없어 전부 {@link #RECEIVED} 에 머문다.</b> 백오피스가 생기면
 * 관리자 API 가 붙는다.
 *
 * <p>그럼에도 지금 두는 것은, 나중에 더하면 <b>이미 쌓인 건의 전부에 기본값을 소급 부여</b>해야
 * 하고 그 시점에는 무엇이 처리됐는지 알 방법이 없기 때문이다.
 *
 * <p><b>DB 는 {@code VARCHAR} 이고 허용값은 이 enum 이 정한다.</b> MySQL {@code ENUM} 을 쓰지
 * 않는 이유는 값 하나 더하는 데 {@code ALTER TABLE} 이 필요하고, 기존 값의 순서를 바꾸면 저장된
 * 행의 뜻이 통째로 어긋나기 때문이다 — 챌린지 카테고리에서 실제로 겪었다.
 */
@Getter
@RequiredArgsConstructor
public enum SuggestionStatus {

    RECEIVED("접수"),
    IN_PROGRESS("처리 중"),
    DONE("처리 완료");

    private final String label;
}
