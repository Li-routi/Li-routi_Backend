package com.lirouti.domain.chat.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatSuccessCode implements BaseSuccessCode {
    MESSAGE_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "채팅 메시지 조회에 성공했습니다.",
            "CHAT200_1"
    ),
    EMOTICON_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "채팅 이모티콘 목록 조회에 성공했습니다.",
            "CHAT200_2"
    ),
    MESSAGE_SEND_SUCCESS(
            HttpStatus.OK,
            "채팅 메시지 전송에 성공했습니다.",
            "CHAT200_3"
    ),
    READ_POSITION_UPDATE_SUCCESS(
            HttpStatus.OK,
            "채팅 읽음 위치 갱신에 성공했습니다.",
            "CHAT200_4"
    ),
    ADMIN_EMOTICON_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "관리자용 채팅 이모티콘 목록 조회에 성공했습니다.",
            "CHAT200_5"
    ),
    EMOTICON_STATUS_UPDATE_SUCCESS(
            HttpStatus.OK,
            "채팅 이모티콘 상태 변경에 성공했습니다.",
            "CHAT200_6"
    ),
    EMOTICON_CREATE_SUCCESS(
            HttpStatus.CREATED,
            "채팅 이모티콘 등록에 성공했습니다.",
            "CHAT201_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
