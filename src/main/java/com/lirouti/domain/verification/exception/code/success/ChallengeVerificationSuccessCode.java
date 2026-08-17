package com.lirouti.domain.verification.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 챌린지 인증의 성공 코드.
 *
 * <p>코드 문자열이 {@code CHALLENGE*}인 이유는 실패 코드와 같다 — 클라이언트가 받는 값이라
 * 도메인을 옮겼다고 바꾸지 않는다. 자세한 근거는
 * {@link com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode}에 적어두었다.
 */
@Getter
@AllArgsConstructor
public enum ChallengeVerificationSuccessCode implements BaseSuccessCode {

    CHALLENGE_VERIFY_SUCCESS(
            HttpStatus.OK,
            "챌린지 인증에 성공했습니다.",
            "CHALLENGE200_6"
    ),
    VERIFICATION_FEED_FETCH_SUCCESS(
            HttpStatus.OK,
            "인증 피드 조회에 성공했습니다.",
            "CHALLENGE200_7"
    ),
    VERIFICATION_REPORT_SUCCESS(
            HttpStatus.OK,
            "인증 신고에 성공했습니다.",
            "CHALLENGE200_8"
    ),
    MY_VERIFICATION_FETCH_SUCCESS(
            HttpStatus.OK,
            "내 인증 목록 조회에 성공했습니다.",
            "CHALLENGE200_9"
    ),
    // 좋아요·취소는 멱등하다. 이미 그 상태여도 실패가 아니라 이 코드로 최종 상태를 돌려준다.
    VERIFICATION_LIKE_SUCCESS(
            HttpStatus.OK,
            "인증 좋아요에 성공했습니다.",
            "CHALLENGE200_10"
    ),
    VERIFICATION_UNLIKE_SUCCESS(
            HttpStatus.OK,
            "인증 좋아요 취소에 성공했습니다.",
            "CHALLENGE200_11"
    ),
    VERIFICATION_MEMO_UPDATE_SUCCESS(
            HttpStatus.OK,
            "인증 메모를 수정했습니다.",
            "CHALLENGE200_12"
    ),
    VERIFICATION_DELETE_SUCCESS(
            HttpStatus.OK,
            "인증 게시글을 삭제했습니다.",
            "CHALLENGE200_13"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
