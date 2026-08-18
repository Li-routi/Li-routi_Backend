package com.lirouti.domain.chat.event;

/** 활성 채팅 이모티콘 공통 목록이 변경되어 캐시를 갱신해야 함을 알리는 이벤트다. */
public record ChatEmoticonCacheInvalidatedEvent() {
}
