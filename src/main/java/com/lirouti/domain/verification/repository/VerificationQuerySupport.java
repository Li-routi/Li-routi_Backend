package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.QChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 인증 조회가 공유하는 QueryDSL 조건.
 *
 * <p>인증 엔티티를 보는 조건은 인증 도메인이 가진다. 챌린지 쪽에 두면 인증 엔티티를 참조하려고
 * 챌린지가 인증을 알고, 인증은 그 조건을 쓰려고 챌린지를 알게 되어 의존이 양방향이 된다.
 */
public final class VerificationQuerySupport {
    private VerificationQuerySupport() {
    }

    /**
     * 신고 누적으로 가려진 인증을 제외한다.
     *
     * <b>작성자 본인에게도 적용된다.</b> 본인만 보이게 하면 /me 조회만 예외가 되어 응답에
     * 상태 필드가 붙고 조회마다 분기가 생긴다. 임시 조치의 범위를 넘는다고 보아 조건 하나로
     * 통일했다(database-schema.md).
     *
     * <b>노출 경로에만 붙인다.</b> 스트릭과 오늘 완료자 수는 수행 기록이지 노출이 아니라서
     * 이 조건을 달지 않는다 — 신고당했다고 달성이 취소되면 안 된다.
     */
    public static BooleanExpression notHidden(QChallengeVerification verification) {
        return verification.hiddenAt.isNull();
    }

    /**
     * 심사 보류 중인 인증을 제외한다.
     *
     * <p><b>노출 경로에만 붙인다.</b> 보류 건은 승격되지 않아 공개 주소가 없다 — 피드에 담으면
     * 열리지 않는 사진이 나가고, 게시글 수에 세면 보이는 것과 세는 것이 어긋난다.
     *
     * <p><b>수행 기록에는 붙이지 않는다.</b> 오늘 완료 여부·완료자 수는 "실제로 했는가"를 세는
     * 것이라 보류도 포함한다. 스트릭을 보류 시점에 올리기로 한 것과 같은 기준이다.
     * 신고 숨김({@link #notHidden})이 노출 경로에만 붙는 것과 같은 갈림이다.
     *
     * <p><b>내 인증 목록에는 붙이지 않는다.</b> 방금 올린 사진이 화면에서 사라지면 안 된다 —
     * 대신 상태를 함께 내려 "심사 중" 을 그리게 하고, 사진은 서명 주소로 준다.
     */
    public static BooleanExpression notPending(QChallengeVerification verification) {
        return verification.reviewStatus.eq(ReviewStatus.APPROVED);
    }

    /**
     * 작성자가 내린 글을 제외한다.
     *
     * <p><b>노출 경로에만 붙인다.</b> 삭제는 "글을 내리는 것" 이지 "인증을 취소하는 것" 이 아니다 —
     * 그래서 현재 주기 인증 여부·오늘 완료자 수 같은 <b>수행 기록에는 붙이지 않는다.</b>
     * 신고 숨김({@link #notHidden})·심사 보류({@link #notPending})와 같은 갈림이다.
     *
     * <p>⚠️ <b>저장 경로의 "오늘 인증 찾기" 에는 절대 붙이면 안 된다.</b> 붙이면 내린 뒤 다시
     * 인증할 때 그 행을 못 찾아 새로 INSERT 하고 유니크 제약에 걸린다. 덮어쓰기 경로가 내린
     * 행까지 찾아야만 소프트 삭제가 성립한다(database-schema.md).
     */
    public static BooleanExpression notDeleted(QChallengeVerification verification) {
        return verification.deletedAt.isNull();
    }
}
