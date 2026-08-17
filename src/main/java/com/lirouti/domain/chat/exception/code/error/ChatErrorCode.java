package com.lirouti.domain.chat.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {
    MESSAGE_CONTENT_INVALID(
            HttpStatus.BAD_REQUEST,
            "텍스트 메시지 본문이 올바르지 않습니다.",
            "CHAT400_1"
    ),
    CLIENT_MESSAGE_ID_INVALID(
            HttpStatus.BAD_REQUEST,
            "클라이언트 메시지 ID가 올바르지 않습니다.",
            "CHAT400_5"
    ),
    MESSAGE_TYPE_INVALID(
            HttpStatus.BAD_REQUEST,
            "채팅 메시지 타입과 내용의 조합이 올바르지 않습니다.",
            "CHAT400_2"
    ),
    EMOTICON_CODE_INVALID(
            HttpStatus.BAD_REQUEST,
            "이모티콘 코드가 올바르지 않습니다.",
            "CHAT400_3"
    ),
    READ_POSITION_INVALID(
            HttpStatus.BAD_REQUEST,
            "읽음 위치가 올바르지 않습니다.",
            "CHAT400_4"
    ),
    MESSAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "채팅 메시지를 찾을 수 없습니다.",
            "CHAT404_1"
    ),
    EMOTICON_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "이모티콘을 찾을 수 없습니다.",
            "CHAT404_2"
    ),
    DUPLICATE_EMOTICON_CODE(
            HttpStatus.CONFLICT,
            "이미 등록된 이모티콘 코드입니다.",
            "CHAT409_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
