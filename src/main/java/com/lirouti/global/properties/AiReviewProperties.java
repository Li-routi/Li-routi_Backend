package com.lirouti.global.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 인증 사진 AI 심사 설정.
 *
 * 사진이 그 챌린지의 의도에 맞는지 Claude 에게 물어 통과 여부를 정한다.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "ai.review")
public class AiReviewProperties {

    /**
     * 심사를 켤지. <b>끄면 자가인증으로 되돌아간다</b>(사진만 올리면 통과).
     *
     * 끌 수 있게 둔 이유는 탈출구다 — 모델이 오작동해 정상 인증을 무더기로 반려하면
     * 코드를 되돌리는 것보다 이 값을 끄고 재배포하는 편이 빠르다.
     * 바이트 검증에 같은 성격의 스위치가 있다.
     */
    private boolean enabled = true;

    /** Anthropic API 키. 기본값을 두지 않아 미주입 시 부팅이 실패한다. */
    @NotBlank(message = "Anthropic API 키는 필수입니다. ANTHROPIC_API_KEY 환경변수를 주입하세요.")
    private String apiKey;

    @NotBlank(message = "AI 심사 모델은 필수입니다.")
    private String model;

    /**
     * 응답 대기 상한.
     *
     * 인증 요청 하나가 이만큼 늦어질 수 있다는 뜻이다. 사용자가 그 시간을 그대로 기다리므로
     * 길게 잡을수록 화면이 멈춘 것처럼 보인다. 짧게 잡으면 정상 판정도 타임아웃으로 흘려보낸다
     * (그때는 fail-open 이라 통과된다 — 막히지는 않는다).
     */
    @NotNull(message = "AI 심사 응답 대기 시간은 필수입니다.")
    private Duration timeout;

    /**
     * AI 에 보내기 전에 줄일 이미지의 긴 변 픽셀.
     *
     * <p>Anthropic 은 긴 변이 이 크기를 넘는 이미지를 <b>어차피 자기가 축소해서</b> 본다.
     * 즉 원본을 그대로 보내도 판정 품질은 같고 전송만 느려진다. 게다가 이미지 한 장에
     * 크기 상한이 있어 큰 사진은 요청 자체가 거부된다.
     *
     * <p>원본은 S3 에 그대로 남는다. 줄인 것은 이 호출에만 쓰고 버린다.
     */
    @Positive(message = "AI 심사 이미지 최대 변 길이는 양수여야 합니다.")
    private int maxImageDimension = 1568;
}
