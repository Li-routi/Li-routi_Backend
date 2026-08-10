package com.lirouti.domain.challenge.repository;

import static com.lirouti.domain.member.repository.MemberQuerySupport.activeMember;
import static com.lirouti.domain.verification.repository.VerificationQuerySupport.notHidden;
import static com.lirouti.domain.verification.repository.VerificationQuerySupport.notDeleted;
import static com.lirouti.domain.verification.repository.VerificationQuerySupport.notPending;
import static com.querydsl.core.group.GroupBy.groupBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.QChallenge;
import com.lirouti.domain.verification.entity.QChallengeVerification;
import com.lirouti.domain.verification.entity.QChallengeVerificationLike;
import com.lirouti.domain.challenge.entity.QMemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.QMember;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChallengeRepositoryImpl implements ChallengeRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    private static final QChallenge challenge = QChallenge.challenge;
    private static final QMemberChallenge memberChallenge = QMemberChallenge.memberChallenge;
    private static final QChallengeVerification verification = QChallengeVerification.challengeVerification;
    private static final QMember member = QMember.member;
    private static final QChallengeVerificationLike like =
            QChallengeVerificationLike.challengeVerificationLike;
    // 좋아요를 누른 회원. 위 member 는 인증 작성자라 별칭을 나눠야 한다.
    private static final QMember liker = new QMember("coverLiker");

    @Override
    public List<Challenge> findByCursor(
            ChallengeCategory category,
            String keyword,
            Long cursor,
            int limit
    ) {
        // 최신순 커서 페이지네이션. id는 auto-increment라 최신일수록 크므로 id 내림차순이 곧 최신순이다.
        // cursor(마지막으로 받은 challengeId)가 있으면 그보다 작은 id만 가져와 이어서 스크롤한다.
        return queryFactory
                .selectFrom(challenge)
                .where(
                        challenge.active.isTrue(),
                        categoryEq(category),
                        nameContains(keyword),
                        cursorLt(cursor)
                )
                .orderBy(challenge.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Map<Long, Long> countActiveParticipantsByChallengeIds(List<Long> challengeIds) {
        if (challengeIds.isEmpty()) {
            return Map.of();
        }
        // challenge_id별 활성 참여자 수. 탈퇴 회원 제외. 참여자 0인 챌린지는 결과에 안 나온다.
        return queryFactory
                .select(memberChallenge.challenge.id, member.id.count())
                .from(memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.in(challengeIds),
                        memberChallenge.active.isTrue(),
                        activeMember(member)
                )
                .groupBy(memberChallenge.challenge.id)
                .transform(groupBy(memberChallenge.challenge.id).as(member.id.count()));
    }

    @Override
    public Map<Long, Long> countVerificationPostsByChallengeIds(List<Long> challengeIds) {
        if (challengeIds.isEmpty()) {
            return Map.of();
        }
        // challenge_id별 인증 게시글 수. 인증(게시글) 단위이므로 회차 중복 제거를 하지 않는다.
        // 탈퇴 회원의 인증과 신고 누적으로 가려진 인증은 제외한다.
        return queryFactory
                .select(memberChallenge.challenge.id, verification.id.count())
                .from(verification)
                .join(verification.memberChallenge, memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.in(challengeIds),
                        activeMember(member),
                        notHidden(verification),
                        // 피드와 같은 수를 보여야 한다. 세는 것과 보이는 것이 다르면 화면이 어긋난다.
                        notPending(verification),
                        notDeleted(verification)
                )
                .groupBy(memberChallenge.challenge.id)
                .transform(groupBy(memberChallenge.challenge.id).as(verification.id.count()));
    }

    /**
     * 챌린지별 <b>대표 이미지 후보</b> — 좋아요가 가장 많은 인증의 사진 key.
     *
     * <h3>표지에서 빼야 하는 것이 넷이다</h3>
     * 대표 이미지는 <b>참여하지 않은 사람에게도 목록에서 보인다.</b> 피드보다 넓게 노출되므로
     * 빠뜨리면 더 크게 드러난다.
     * <ul>
     *   <li>{@code notHidden} — 신고로 가린 사진이 표지가 되면 숨김이 무의미하다</li>
     *   <li>{@code notPending} — <b>심사를 안 지난 사진이 가장 눈에 띄는 자리에 걸린다.</b>
     *       비공개 대기 prefix 로 받기로 한 이유가 통째로 무너진다</li>
     *   <li>{@code notDeleted} — 지운 사람의 사진이 표지로 남는다</li>
     *   <li>{@code activeMember} — 탈퇴 회원. 다른 집계와 같은 기준이다</li>
     * </ul>
     *
     * <p><b>조회자별 신고({@code notReportedBy})는 넣지 않는다.</b> 표지는 모두에게 같은 값이라
     * 조회자별로 갈리면 캐시도 못 하고 "내가 신고한 사진이 표지" 라는 상태도 남는다.
     *
     * <h3>tie-break 가 없으면 표지가 매번 바뀐다</h3>
     * <b>대부분의 챌린지는 좋아요가 전부 0이다.</b> 2차 기준이 없으면 목록을 새로 고칠 때마다
     * 카드 사진이 달라진다. {@code id} 내림차순으로 못 박는다 — 최신 인증이 표지가 된다.
     *
     * <p>인증이 하나도 없는 챌린지는 결과에 나오지 않는다(호출부가 {@code null} 로 다룬다).
     */
    @Override
    public Map<Long, String> coverImagesByChallengeIds(List<Long> challengeIds) {
        if (challengeIds.isEmpty()) {
            return Map.of();
        }
        // 챌린지별 1위를 SQL 한 방으로 뽑으려면 윈도 함수(ROW_NUMBER)가 필요한데
        // JPQL·QueryDSL(JPA)에서는 쓸 수 없다. 후보를 정렬된 채로 한 번에 가져와 첫 건만
        // 남긴다 — 챌린지 수만큼 쿼리가 나가는 N+1 은 피하면서 제외 규칙은 한 곳에 둔다.
        //
        // 대가는 분명하다: 돌아오는 행이 "이 페이지 챌린지들의 보이는 인증 전부" 다. 인증이
        // 쌓이면 카드 열 장에 수천 행이 될 수 있다. 지금 규모에서는 문제가 아니지만 공짜도
        // 아니다. 네이티브 윈도 함수로 바꾸면 챌린지당 한 행으로 줄어드는 대신 제외 조건이
        // QueryDSL 헬퍼에서 떨어져 나가 규칙이 갈라진다 — 인증 조회가 무거워지는 문제를
        // 한꺼번에 볼 때(피드 인덱스 전략) 같이 정한다.
        List<Tuple> rows = queryFactory
                .select(memberChallenge.challenge.id, verification.imageUrl)
                .from(verification)
                .join(verification.memberChallenge, memberChallenge)
                .join(memberChallenge.member, member)
                .leftJoin(like).on(like.challengeVerification.eq(verification))
                .leftJoin(like.member, liker)
                .where(
                        memberChallenge.challenge.id.in(challengeIds),
                        activeMember(member),
                        notHidden(verification),
                        notPending(verification),
                        notDeleted(verification)
                )
                .groupBy(memberChallenge.challenge.id, verification.id, verification.imageUrl)
                .orderBy(memberChallenge.challenge.id.asc(),
                        coverLikeCount().desc(), verification.id.desc())
                .fetch();

        Map<Long, String> covers = new LinkedHashMap<>();
        for (Tuple row : rows) {
            // 정렬이 이미 챌린지별 1위를 앞에 두므로 처음 것만 담는다.
            covers.putIfAbsent(row.get(memberChallenge.challenge.id), row.get(verification.imageUrl));
        }
        return covers;
    }

    /** 표지 선정에 쓰는 좋아요 수. <b>탈퇴 회원은 빼고 센다</b>(다른 집계와 같은 규칙). */
    private NumberExpression<Long> coverLikeCount() {
        return new CaseBuilder()
                .when(activeMember(liker)).then(liker.id)
                .otherwise((Long) null)
                .count();
    }

    @Override
    public long countActiveParticipants(Long challengeId) {
        Long count = queryFactory
                .select(member.id.count())
                .from(memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.eq(challengeId),
                        memberChallenge.active.isTrue(),
                        activeMember(member)
                )
                .fetchOne();
        return (count != null) ? count : 0L;
    }

    @Override
    public long countVerificationPosts(Long challengeId) {
        // 인증(게시글) 단위 집계. 회차 중복 제거를 하지 않는다. 탈퇴 회원의 인증과 신고 누적으로 가려진 인증은 제외한다.
        // 목록의 배치 집계(countVerificationPostsByChallengeIds)와 같은 기준을 단건으로 센 것이다.
        Long count = queryFactory
                .select(verification.id.count())
                .from(verification)
                .join(verification.memberChallenge, memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.eq(challengeId),
                        activeMember(member),
                        notHidden(verification),
                        // 피드와 같은 수를 보여야 한다. 세는 것과 보이는 것이 다르면 화면이 어긋난다.
                        notPending(verification),
                        notDeleted(verification)
                )
                .fetchOne();
        return (count != null) ? count : 0L;
    }

    private BooleanExpression cursorLt(Long cursor) {
        return (cursor != null) ? challenge.id.lt(cursor) : null;
    }

    private BooleanExpression categoryEq(ChallengeCategory category) {
        return (category != null) ? challenge.category.eq(category) : null;
    }

    private BooleanExpression nameContains(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? challenge.name.contains(keyword.trim()) : null;
    }
}
