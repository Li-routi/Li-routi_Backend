package com.lirouti.domain.chat.enums;

/**
 * 그룹 채팅에서 서버가 저장·전달하는 메시지 종류다.
 *
 * 시스템 이모지는 TEXT의 Unicode 문자열로 처리하고, EMOTICON은 서비스 자산을 참조한다.
 */
public enum ChatMessageType {
    TEXT,
    EMOTICON
}
