package com.lirouti.domain.media.service;

/**
 * AI 심사에 보낼 이미지 한 장.
 *
 * @param bytes    실제 바이트. 축소했으면 축소본, 아니면 원본 그대로다
 * @param mimeType 그 바이트의 형식. 축소하면 JPEG 으로 바뀌므로 원본 확장자와 다를 수 있다
 */
public record MediaImage(byte[] bytes, String mimeType) {
}
