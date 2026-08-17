package com.lirouti.domain.media.service;

/**
 * AI 심사에 보낼 이미지 한 장.
 *
 * @param bytes    실제 바이트. 축소했으면 축소본, 아니면 원본 그대로다
 * @param mimeType 그 바이트의 형식. 축소하면 JPEG 으로 바뀌므로 원본 확장자와 다를 수 있다
 * @param etag     <b>심사한 그 오브젝트의 ETag.</b> 승격할 때 같은 바이트인지 확인하는 데 쓴다 —
 *                 presigned URL 은 만료 전까지 여러 번 쓸 수 있어, 심사와 승격 사이에 같은 key 로
 *                 다른 사진을 올리면 심사하지 않은 바이트가 공개될 수 있다
 */
public record MediaImage(byte[] bytes, String mimeType, String etag) {
}
