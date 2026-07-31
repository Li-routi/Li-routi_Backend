package com.lirouti.domain.media.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.lirouti.domain.media.converter.MediaConverter;
import com.lirouti.domain.media.dto.request.MediaReqDTO;
import com.lirouti.domain.media.dto.response.MediaResDTO;
import com.lirouti.domain.media.enums.MediaCategory;
import com.lirouti.domain.media.enums.MediaContentType;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.properties.S3Properties;
import com.lirouti.global.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 미디어 업로드용 presigned URL을 발급하고, 저장된 미디어 key의 검증·URL 조립을 담당한다.
 * DB에는 오브젝트 key만 저장하므로(database-schema.md), key ↔ 공개 URL 규칙은 이 클래스가 소유한다.
 * DB를 다루지 않아 조회/변경 구분이 무의미하므로 CQRS를 적용하지 않는다.
 * (service_convention.md의 CQRS 예외 도메인)
 *
 * 현재는 사진만 업로드한다. 영상은 형식·용도·용량 구조만 갖춰두었고 실제로 켜져 있지 않다.
 * 영상을 켜려면 MediaContentType에 영상 형식을 추가하고, 해당 MediaPurpose의 허용 카테고리에
 * MediaCategory.VIDEO를 넣으면 이 검증 흐름이 그대로 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {
    private final S3Presigner s3Presigner;
    // 업로드된 바이트를 실제로 읽어 검증하기 위한 클라이언트(#22). presigner와 달리 S3를 호출한다.
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public MediaResDTO.PresignedUrl issuePresignedUrl(MediaReqDTO.PresignedUrl request) {
        MediaContentType contentType = resolveContentType(request.contentType());
        validatePurposeAllows(request.purpose(), contentType);
        validateFileSize(contentType.getCategory(), request.contentLength());

        String mediaKey = generateMediaKey(request.purpose(), contentType);
        Duration expiration = s3Properties.getPresignedUrlExpiration();

        // 서명에는 정규화된 MIME 값을 쓴다. 요청 원본(대소문자·공백)이 아니라 이 값이 서명에 들어가므로,
        // 클라이언트는 응답으로 돌려주는 이 값을 그대로 PUT 헤더에 실어야 서명이 일치한다.
        String signedContentType = contentType.getMimeType();

        // 서명에 Content-Type과 Content-Length를 포함시킨다.
        // 클라이언트가 다른 형식이나 크기로 업로드하면 S3가 거부하므로,
        // 서버가 파일 바이트를 보지 않고도 검증 결과를 강제할 수 있다.
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(mediaKey)
                .contentType(signedContentType)
                .contentLength(request.contentLength())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = presign(presignRequest, mediaKey);
        log.debug("미디어 업로드 URL을 발급했습니다. key={}", mediaKey);

        // 만료 시각은 로컬에서 재계산하지 않고, SDK가 서명에 실제로 부여한 값을 그대로 쓴다.
        // now().plus(...)는 서명 시점과 미세하게 어긋날 수 있다.
        return MediaConverter.toPresignedUrl(
                presigned.url().toString(),
                mediaKey,
                resolvePublicUrl(mediaKey),
                signedContentType,
                request.contentLength(),
                presigned.expiration()
        );
    }

    /**
     * 저장된 오브젝트 key를 클라이언트가 읽을 수 있는 공개 URL로 조립한다.
     * DB에는 key만 저장하므로 조회 응답을 만들 때마다 이 메서드를 거친다.
     * public-base-url은 필수 설정이라 null·빈 값은 부팅 시점에 걸러진다.
     */
    public String resolvePublicUrl(String mediaKey) {
        String base = s3Properties.getPublicBaseUrl();
        String normalizedBase = base.endsWith("/")
                ? base.substring(0, base.length() - 1)
                : base;
        return normalizedBase + "/" + mediaKey;
    }

    /**
     * 클라이언트가 보낸 key가 그 용도로 발급한 key의 형식인지 검증한다.
     *
     * key는 서버가 발급하지만 업로드 후 요청 본문으로 되돌아오므로 그대로 믿을 수 없다.
     * 다른 용도의 경로나 임의 문자열이 저장되지 않도록 발급 규칙과 대조한다. 날짜가 붙은 현재 형태와
     * 날짜 도입 전의 flat 형태를 모두 받는다(#39, MediaKeyFormat 참고).
     * 실제 오브젝트가 업로드됐는지, 그 바이트가 정말 이미지인지는 확인하지 않는다(#22·#19 범위).
     */
    public void validateMediaKey(String mediaKey, MediaPurpose purpose) {
        if (!matchesIssuedKeyFormat(mediaKey, purpose)) {
            log.warn("발급 규칙에 맞지 않는 미디어 key입니다. purpose={}, mediaKey={}", purpose, mediaKey);
            throw new MediaException(MediaErrorCode.INVALID_MEDIA_KEY);
        }
    }

    /**
     * 업로드된 오브젝트의 <b>실제 바이트</b>가 key의 확장자와 맞는 형식인지 확인한다(#22).
     *
     * presigned URL은 서명에 Content-Type·Content-Length를 넣어 <b>요청 메타데이터</b>를 강제하지만,
     * 바이트 내용까지 강제하지는 못한다. 즉 {@code Content-Type: image/jpeg}로 선언하고 임의의
     * 바이너리를 올리는 것을 막지 못한다. 그 오브젝트가 공개 피드에 그대로 노출되면 문제가 된다.
     *
     * <p><b>앞 12바이트만 읽는다.</b> Range GET이라 파일이 커도 비용이 일정하고, 전체를 받아
     * 디코딩하면 압축 폭탄(작은 파일이 거대한 이미지로 풀리는 것)에 노출된다. 매직 넘버 대조는
     * "이 바이트가 정말 JPEG/PNG/WEBP인가"까지만 답한다 — 내용이 무엇인지는 판단하지 않으며
     * 그건 AI 심사(#40) 몫이다.
     *
     * <p><b>트랜잭션 밖에서 호출해야 한다.</b> 외부 API 호출이라 트랜잭션 안에서 부르면
     * DB 커넥션·행 락을 S3 왕복 시간만큼 붙잡는다(service_convention).
     *
     * @throws MediaException 업로드가 안 됐거나(404), 바이트가 형식과 다르거나(422),
     *                        S3 조회 자체가 실패한 경우(500)
     */
    public void validateUploadedBytes(String mediaKey, MediaPurpose purpose) {
        if (!s3Properties.isByteValidationEnabled()) {
            log.debug("바이트 검증이 꺼져 있어 건너뜁니다. mediaKey={}", mediaKey);
            return;
        }
        // 확장자는 앞선 형식 검증(validateMediaKey)을 통과한 값이라 반드시 매칭된다.
        MediaContentType expected = resolveTypeByExtension(mediaKey);
        byte[] head = readHead(mediaKey);

        if (!expected.matchesSignature(head)) {
            log.warn("업로드된 바이트가 선언한 형식과 다릅니다. purpose={}, expected={}, mediaKey={}",
                    purpose, expected, mediaKey);
            throw new MediaException(MediaErrorCode.MEDIA_CONTENT_MISMATCH);
        }
    }

    /**
     * AI 심사에 보낼 이미지를 읽어 온다. 필요하면 긴 변을 줄인다.
     *
     * <p><b>줄이는 이유는 화질이 아니라 전송이다.</b> 심사 쪽은 긴 변이 일정 크기를 넘는
     * 이미지를 어차피 자기가 축소해서 보므로, 원본을 그대로 보내면 판정은 같고 전송만 느려진다.
     * 게다가 이미지 한 장에 크기 상한이 있어 큰 사진은 요청 자체가 거부된다.
     *
     * <p><b>원본은 건드리지 않는다.</b> S3 오브젝트는 그대로 두고, 줄인 바이트는 이 호출에만 쓴다.
     *
     * <p>디코딩할 수 없는 형식(WEBP 는 표준 ImageIO 로 못 읽는다)은 <b>원본 그대로 돌려준다.</b>
     * 심사 쪽이 그 형식을 받아주므로 크기만 감당되면 그대로 보내도 된다.
     *
     * <h3>메모리 상한이 이 메서드의 핵심이다</h3>
     * 이 서버는 힙이 768m 다. 전체 바이트를 올리고 전체를 디코딩하면 파일이 작아도
     * 픽셀 수가 크면 터진다 — 디코딩은 {@code 가로 × 세로 × 4} 바이트를 잡으므로
     * 1200만 화소 사진 한 장이 약 48MB 다. 인증이 몰리면 그것이 동시에 여러 벌 생긴다.
     * 같은 클래스의 {@code readHead} 가 앞 12바이트만 읽는 것도 같은 이유였다.
     *
     * <p>그래서 두 겹으로 막는다.
     * <ol>
     *   <li>S3 응답의 {@code contentLength} 로 업로드 상한을 넘는 오브젝트를 먼저 거른다.
     *       그 상한은 presigned URL 발급 때 이미 강제하므로, 넘는 것은 발급을 거치지 않고
     *       버킷에 직접 쓰인 오브젝트다.</li>
     *   <li>헤더에서 <b>크기만</b> 읽어 서브샘플링 배수를 정한 뒤 <b>줄여서 디코딩</b>한다.
     *       전체를 펼쳤다가 줄이는 것이 아니라 처음부터 작게 읽으므로, 원본 화소 수와 무관하게
     *       메모리가 목표 크기 근처로 묶인다.</li>
     * </ol>
     *
     * @return 읽지 못했거나 상한을 넘으면 빈 값. 호출부는 이것을 "심사 못 함"으로 다루고 막지 않는다.
     */
    public Optional<MediaImage> loadForReview(String mediaKey, int maxDimension) {
        byte[] original;
        String mimeType;
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(mediaKey)
                    .build();
            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
                long contentLength = response.response().contentLength();
                if (contentLength > s3Properties.getMaxImageSize()) {
                    log.warn("심사용 이미지가 업로드 상한을 넘어 심사를 건너뜁니다."
                                    + " mediaKey={}, contentLength={}, max={}",
                            mediaKey, contentLength, s3Properties.getMaxImageSize());
                    return Optional.empty();
                }
                original = response.readAllBytes();
                mimeType = resolveTypeByExtension(mediaKey).getMimeType();
            }
        } catch (SdkException | IOException e) {
            log.warn("심사용 이미지를 읽지 못했습니다. mediaKey={}", mediaKey, e);
            return Optional.empty();
        }

        return Optional.of(downscale(original, mimeType, maxDimension, mediaKey));
    }

    /**
     * 긴 변이 상한을 넘으면 비율을 지켜 줄이고 JPEG 으로 다시 쓴다.
     *
     * 실패하면 원본을 그대로 돌려준다. 줄이기는 최적화이지 검증이 아니라서, 여기서 막으면
     * 디코딩 못 하는 형식 하나 때문에 심사가 통째로 사라진다.
     */
    private MediaImage downscale(byte[] original, String mimeType, int maxDimension, String mediaKey) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                // ImageIO 가 읽지 못하는 형식(WEBP 등). 심사 쪽이 받아주므로 원본을 보낸다.
                return new MediaImage(original, mimeType);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                // 여기까지는 헤더만 읽는다. 픽셀은 아직 메모리에 올라오지 않았다.
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                int longEdge = Math.max(sourceWidth, sourceHeight);
                if (longEdge <= maxDimension) {
                    return new MediaImage(original, mimeType);
                }

                BufferedImage source = readSubsampled(reader, longEdge, maxDimension);
                return new MediaImage(toJpeg(source, sourceWidth, sourceHeight, maxDimension),
                        MediaContentType.JPEG.getMimeType());
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("심사용 이미지를 줄이지 못해 원본을 그대로 보냅니다. mediaKey={}", mediaKey, e);
            return new MediaImage(original, mimeType);
        }
    }

    /**
     * 목표 크기에 가깝게 <b>줄여서 디코딩</b>한다.
     *
     * 서브샘플링은 픽셀을 건너뛰며 읽으므로 전체를 펼치지 않는다. 배수는 목표보다 작아지지
     * 않도록 내림으로 잡고, 남은 차이는 뒤에서 정확한 크기로 맞춘다 — 여기서 목표보다 작게
     * 읽어 버리면 다시 키워야 해서 화질만 나빠진다.
     */
    private BufferedImage readSubsampled(ImageReader reader, int longEdge, int maxDimension)
            throws IOException {
        int step = Math.max(1, longEdge / maxDimension);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, param);
    }

    /** 정확한 목표 크기로 맞춰 JPEG 으로 쓴다. */
    private byte[] toJpeg(BufferedImage source, int sourceWidth, int sourceHeight, int maxDimension)
            throws IOException {
        double ratio = (double) maxDimension / Math.max(sourceWidth, sourceHeight);
        int width = Math.max(1, (int) Math.round(sourceWidth * ratio));
        int height = Math.max(1, (int) Math.round(sourceHeight * ratio));

        // 알파가 있는 PNG 를 그대로 JPEG 으로 쓰면 투명 부분이 검게 나온다. 흰 배경에 얹는다.
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, Color.WHITE, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(scaled, "jpg", out)) {
            throw new IOException("JPEG 인코더를 찾지 못했습니다.");
        }
        return out.toByteArray();
    }

    /**
     * 오브젝트를 지운다. 실패해도 예외를 던지지 않는다.
     *
     * <p>심사에서 반려된 사진을 그 자리에서 치우는 용도다. 반려는 저장을 안 하므로 DB 가
     * 그 key 를 참조하지 않고, 그대로 두면 미참조 이미지 정리가 며칠 뒤에 가져간다.
     * 굳이 지금 지우는 이유는 <b>반려된 사진이 유해한 것일 수 있기 때문</b>이다 —
     * 공개 prefix 라 key 를 아는 사람은 그동안 계속 볼 수 있다.
     *
     * <p><b>다만 이것으로 노출을 막지는 못한다.</b> 사진은 심사 전에 이미 공개 prefix 에
     * 올라가 있고 발급 응답이 그 공개 URL 을 함께 준다. 업로더는 심사 전에 그 URL 을 열거나
     * 공유할 수 있고, 지운 뒤에도 이미 받아 간 사본은 회수되지 않는다.
     * 제대로 막으려면 심사 전 사진을 비공개 staging 에 두고 통과한 뒤 승격해야 한다.
     * 이 메서드는 그때까지의 <b>노출 시간을 줄이는</b> 조치다.
     *
     * <p><b>실패해도 조용히 넘어간다.</b> 이 삭제가 실패했다고 반려 응답이 성공으로 바뀌면
     * 안 되고, 못 지운 오브젝트는 미참조 이미지 정리가 결국 가져간다. 즉 이 메서드는
     * "빨리 지우는" 최적화이지 유일한 삭제 경로가 아니다.
     */
    public void deleteQuietly(String mediaKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(mediaKey)
                    .build());
            log.info("반려된 인증 사진을 삭제했습니다. mediaKey={}", mediaKey);
        } catch (SdkException e) {
            log.warn("반려된 인증 사진을 지우지 못했습니다. 미참조 정리가 나중에 가져갑니다. mediaKey={}",
                    mediaKey, e);
        }
    }

    /** 오브젝트 앞부분만 Range로 읽는다. 없으면 404, 그 밖의 실패는 500으로 바꾼다. */
    private byte[] readHead(String mediaKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(mediaKey)
                // bytes=0-11 → 앞 12바이트. 파일이 그보다 짧으면 있는 만큼만 온다.
                .range("bytes=0-" + (MediaContentType.SIGNATURE_LENGTH - 1))
                .build();
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            log.warn("업로드되지 않은 미디어 key입니다. mediaKey={}", mediaKey);
            throw new MediaException(MediaErrorCode.MEDIA_NOT_UPLOADED);
        } catch (S3Exception e) {
            // 404뿐 아니라 403도 "업로드 안 됨"으로 본다.
            //
            // S3는 ListBucket이 없는 주체에게는 오브젝트 존재 여부를 숨기려고, 없는 key에도
            // NoSuchKey 대신 AccessDenied(403)를 준다. 실측으로 확인한 동작이다(deploy/README.md).
            // 그래서 403을 500으로 돌리면 "업로드를 안 한 클라이언트"에게 서버 오류라고 알려주게 된다.
            //
            // 대신 이렇게 하면 진짜 권한 문제도 404로 보이는 맹점이 생긴다. 다만 그 경우
            // 정상 업로드된 key까지 전부 실패하므로 개별 요청이 아니라 전 요청이 404가 된다 —
            // 아래 로그가 몰려 찍히면 업로드 누락이 아니라 정책 문제로 봐야 한다.
            //
            // 이 분기의 전제가 바뀌는 중이다. 미참조 이미지 정리를 위해 IAM 역할에
            // challenge-verifications/ 아래 ListBucket을 주기로 했고, 그게 콘솔에 반영되면
            // 그 prefix의 없는 key는 403이 아니라 404를 준다. 그때부터 여기의 403은
            // "업로드 누락"이 아니라 진짜 권한 문제만 뜻하므로 이 분기를 떼는 편이 진단에 낫다.
            //
            // 지금 떼지 않는 이유는 콘솔 반영 전까지는 여전히 403이 "없는 key"이기 때문이다.
            // 먼저 떼면 그 사이 업로드를 거른 요청이 전부 500을 받는다. 권한 부착을 확인한 뒤
            // 정리한다.
            int status = e.statusCode();
            if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.FORBIDDEN.value()) {
                log.warn("업로드된 오브젝트를 읽지 못했습니다(status={}). 업로드 누락으로 처리합니다."
                        + " 이 로그가 계속 몰려 찍히면 s3:GetObject 권한을 확인하세요. mediaKey={}",
                        status, mediaKey);
                throw new MediaException(MediaErrorCode.MEDIA_NOT_UPLOADED);
            }
            // 0바이트 오브젝트는 Range GET에 416(InvalidRange)을 준다. 시작 오프셋 0조차
            // 객체 범위 밖이기 때문이다(1바이트만 있어도 있는 만큼 돌려주므로 416이 아니다).
            //
            // 읽을 바이트가 없다는 뜻이니 "이미지가 아니다"와 같은 결론이고, 사용자에게
            // 서버 오류(500)라고 답할 일이 아니다.
            //
            // 정상 경로로는 도달하지 않는다 — contentLength는 @Positive로 막히고, presigned URL은
            // 그 길이를 서명에 넣어 다른 크기의 PUT을 거부한다. 발급 경로를 거치지 않고 버킷에
            // 직접 쓰인 오브젝트를 위한 방어다.
            if (status == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
                log.warn("업로드된 오브젝트가 비어 있습니다(416). mediaKey={}", mediaKey);
                throw new MediaException(MediaErrorCode.MEDIA_CONTENT_MISMATCH);
            }
            log.error("미디어 바이트 조회에 실패했습니다. mediaKey={}", mediaKey, e);
            throw new MediaException(MediaErrorCode.MEDIA_VALIDATION_FAILED);
        } catch (SdkException | IOException e) {
            // 권한·네트워크·타임아웃. 사용자 잘못이 아니므로 5xx로 돌려주고 저장은 막는다.
            log.error("미디어 바이트 조회에 실패했습니다. mediaKey={}", mediaKey, e);
            throw new MediaException(MediaErrorCode.MEDIA_VALIDATION_FAILED);
        }
    }

    private MediaContentType resolveTypeByExtension(String mediaKey) {
        String extension = mediaKey.substring(mediaKey.lastIndexOf('.') + 1);
        return Arrays.stream(MediaContentType.values())
                .filter(type -> type.getExtension().equals(extension))
                .findFirst()
                .orElseThrow(() -> new MediaException(MediaErrorCode.INVALID_MEDIA_KEY));
    }

    private boolean matchesIssuedKeyFormat(String mediaKey, MediaPurpose purpose) {
        if (mediaKey == null) {
            return false;
        }
        String prefix = purpose.getPathPrefix() + "/";
        if (!mediaKey.startsWith(prefix)) {
            return false;
        }
        String fileName = mediaKey.substring(prefix.length());
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0) {
            return false;
        }
        String baseName = fileName.substring(0, extensionSeparator);
        String extension = fileName.substring(extensionSeparator + 1);

        return MediaKeyFormat.isIssuedBody(baseName)
                && MediaKeyFormat.isAllowedExtension(purpose, extension);
    }

    private PresignedPutObjectRequest presign(PutObjectPresignRequest presignRequest, String mediaKey) {
        try {
            return s3Presigner.presignPutObject(presignRequest);
        } catch (RuntimeException e) {
            log.error("S3 presigned URL 발급에 실패했습니다. key={}", mediaKey, e);
            throw new MediaException(MediaErrorCode.PRESIGNED_URL_ISSUE_FAILED);
        }
    }

    private MediaContentType resolveContentType(String contentType) {
        return MediaContentType.from(contentType)
                .orElseThrow(() -> {
                    log.warn("허용하지 않는 미디어 형식으로 업로드를 시도했습니다. contentType={}", contentType);
                    return new MediaException(MediaErrorCode.UNSUPPORTED_CONTENT_TYPE);
                });
    }

    /**
     * 형식 자체는 허용되더라도, 용도가 그 카테고리를 허용하지 않으면 거부한다.
     * (예: 영상이 켜졌을 때 프로필에는 사진만 허용) 사진만 운영하는 현재는
     * 모든 용도가 IMAGE만 허용하므로, 사진 형식 요청은 이 검증을 통과한다.
     */
    private void validatePurposeAllows(MediaPurpose purpose, MediaContentType contentType) {
        if (!purpose.allows(contentType.getCategory())) {
            log.warn("해당 용도에서 허용하지 않는 형식입니다. purpose={}, contentType={}",
                    purpose, contentType.getMimeType());
            throw new MediaException(MediaErrorCode.CONTENT_TYPE_NOT_ALLOWED_FOR_PURPOSE);
        }
    }

    private void validateFileSize(MediaCategory category, long contentLength) {
        long maxFileSize = maxFileSizeFor(category);
        if (contentLength > maxFileSize) {
            log.warn("최대 용량을 초과한 업로드를 시도했습니다. category={}, contentLength={}, max={}",
                    category, contentLength, maxFileSize);
            throw new MediaException(MediaErrorCode.FILE_TOO_LARGE);
        }
    }

    private long maxFileSizeFor(MediaCategory category) {
        return switch (category) {
            case IMAGE -> s3Properties.getMaxImageSize();
            case VIDEO -> s3Properties.getMaxVideoSize();
        };
    }

    /**
     * {@code {용도}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}} 형태로 만든다(#39).
     *
     * 리프는 UUID다. key는 추측할 수 없어야 하고, 클라이언트가 보낸 파일명은 신뢰하지 않는다
     * (경로 조작 방지).
     *
     * <p>날짜를 넣는 이유는 미참조 이미지 정리(#19)다. 날짜 prefix가 있으면 하루치만 훑어
     * 고아 파일을 찾을 수 있고, 없으면 용도 전체를 스캔해야 한다.
     *
     * <p><b>이 날짜는 "업로드일"이다.</b> 발급은 인증보다 먼저 일어나므로 인증일을 담을 수 없다.
     * 23:59에 발급받아 00:01에 인증하면 경로는 어제, DB는 오늘이 된다.
     * <b>업무 판정에 경로의 날짜를 쓰지 않는다</b> — 판정은 항상 DB 컬럼을 본다.
     *
     * <p>공개 용도에는 challengeId·memberId 같은 식별자를 넣지 않는다. 공개 prefix는 경로가
     * URL에 그대로 노출되므로(#66) 식별자를 넣으면 URL만 보고 누가 무엇을 했는지 알 수 있다.
     * 비공개 용도의 접근 범위 식별자 규칙은 database-schema.md에 정의돼 있다(해당 미디어가
     * 생길 때 구현한다).
     */
    private String generateMediaKey(MediaPurpose purpose, MediaContentType contentType) {
        return "%s/%s/%s.%s".formatted(
                purpose.getPathPrefix(),
                LocalDate.now(TimeUtil.KST).format(MediaKeyFormat.KEY_DATE_PATH),
                UUID.randomUUID(),
                contentType.getExtension()
        );
    }
}
