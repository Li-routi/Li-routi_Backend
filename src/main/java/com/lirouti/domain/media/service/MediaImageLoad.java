package com.lirouti.domain.media.service;

/**
 * 심사용 이미지를 읽어 본 결과.
 *
 * <p>예전에는 {@code Optional<MediaImage>} 였는데, <b>빈 값 하나가 두 가지를 함께 뜻했다</b> —
 * S3 를 못 읽은 것과 사진이 상한을 넘은 것. 앞은 다시 하면 될 수 있고 뒤는 몇 번을 해도 같은데,
 * 같은 값으로 돌아오니 호출부가 가를 수가 없었다.
 *
 * @param image     읽어낸 이미지. 실패했으면 null
 * @param failure   실패 원인. 성공했으면 null
 */
public record MediaImageLoad(MediaImage image, Failure failure) {

    /** 왜 못 읽었는가. 보류할지 통과할지가 여기서 갈린다. */
    public enum Failure {
        /** S3 를 읽지 못했다. 일시 오류일 수 있으므로 <b>다시 시도할 가치가 있다.</b> */
        READ_FAILED,
        /** 사진이 심사 상한을 넘었다. <b>다시 읽어도 같다.</b> */
        TOO_LARGE
    }

    public static MediaImageLoad loaded(MediaImage image) {
        return new MediaImageLoad(image, null);
    }

    public static MediaImageLoad readFailed() {
        return new MediaImageLoad(null, Failure.READ_FAILED);
    }

    public static MediaImageLoad tooLarge() {
        return new MediaImageLoad(null, Failure.TOO_LARGE);
    }

    public boolean isLoaded() {
        return image != null;
    }
}
