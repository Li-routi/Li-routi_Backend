package com.lirouti.domain.popup.service.command;

/**
 * 팝업 발행 요청. <b>발행하는 쪽이 보여줄 내용을 전부 채운다.</b>
 *
 * @param memberId      받을 사람
 * @param popupType     소비자가 정하는 종류. 모듈은 해석하지 않는다
 * @param title         화면에 그대로 뜬다
 * @param body          화면에 그대로 뜬다
 * @param imageKey      <b>공개 자산의 S3 key</b>. 없으면 {@code null}
 * @param referenceType 눌렀을 때 보낼 곳의 종류. 없으면 {@code null}
 * @param referenceId   그 대상의 id. 없으면 {@code null}
 * @param dedupKey      같은 사건이면 <b>언제 계산해도 같은 문자열</b>이어야 한다
 */
public record PopupPublishCommand(
        Long memberId,
        String popupType,
        String title,
        String body,
        String imageKey,
        String referenceType,
        Long referenceId,
        String dedupKey
) {
}
