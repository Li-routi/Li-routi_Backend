package com.lirouti.domain.verification.service.query;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.verification.converter.VerificationConverter;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 루틴 인증 조회. <b>이 서비스의 본질은 "누가 볼 수 있는가"를 판정하는 것이다.</b>
 *
 * <p>루틴 인증 사진은 비공개 prefix 에 있어 주소만으로는 열리지 않는다. 열리게 하려면 서명을
 * 붙여야 하는데, <b>서명을 붙이는 순간 그 URL 을 가진 사람은 누구나 볼 수 있다.</b> 그래서
 * 접근 판정이 서명보다 먼저 와야 하고, 그 순서가 이 클래스의 각 메서드 첫머리에 있다.
 *
 * <table>
 *   <caption>대상별 접근 범위</caption>
 *   <tr><th>인증</th><th>볼 수 있는 사람</th><th>판정 방법</th></tr>
 *   <tr><td>개인 루틴</td><td>본인만</td><td>루틴 소유자 확인</td></tr>
 *   <tr><td>그룹 루틴</td><td>그 방 멤버 전원</td><td>{@link GroupValidationService}</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineVerificationQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final MediaService mediaService;
    private final GroupValidationService groupValidationService;
    private final MemberRoutineRepository memberRoutineRepository;
    private final GroupRoutineRepository groupRoutineRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;
    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;

    /**
     * 내 개인 루틴 인증 목록.
     *
     * <p>비활성 루틴도 조회된다. 루틴을 끈 것과 그때까지의 기록을 못 보는 것은 다른 얘기다.
     * 없는 루틴과 남의 루틴은 구분하지 않고 404 로 묶는다 — 구분해서 알려주면 응답만으로
     * 그 id 의 존재 여부가 드러난다(인증 API 와 같은 기준).
     */
    @Transactional(readOnly = true)
    public VerificationResDTO.MemberRoutineFeed getMemberRoutineVerifications(
            Long memberId,
            Long routineId,
            Long cursor,
            Integer size
    ) {
        if (!memberRoutineRepository.existsByIdAndMemberId(routineId, memberId)) {
            log.warn("조회할 수 없는 루틴의 인증 목록을 요청했습니다. memberId={}, routineId={}",
                    memberId, routineId);
            throw new VerificationException(VerificationErrorCode.ROUTINE_NOT_FOUND);
        }

        int appliedSize = clampSize(size);
        List<MemberRoutineVerification> rows = memberRoutineVerificationRepository
                .findMineByCursor(routineId, cursor, Limit.of(appliedSize + 1));
        CursorPage<MemberRoutineVerification> page =
                sliceByCursor(rows, appliedSize, MemberRoutineVerification::getId);

        Map<Long, String> imageUrls = signImageUrls(
                page.rows(),
                MemberRoutineVerification::getId,
                MemberRoutineVerification::getImageUrl,
                MediaPurpose.MEMBER_ROUTINE_VERIFICATION);

        return VerificationConverter.toMemberRoutineFeed(
                page.rows(), imageUrls, page.nextCursor(), page.hasNext());
    }

    /**
     * 그룹 루틴 인증 목록. 그 방 멤버 전원의 인증이 최신순으로 함께 나온다.
     *
     * <p>방 멤버 검증을 먼저 하고 루틴이 그 방 것인지를 뒤에 확인한다. 순서가 중요하다 —
     * 루틴 확인을 먼저 하면 방 멤버가 아닌 사람도 응답 차이로 "그 방에 그 루틴이 있는지"를
     * 알아낼 수 있다.
     */
    @Transactional(readOnly = true)
    public VerificationResDTO.GroupRoutineFeed getGroupRoutineVerifications(
            Long memberId,
            Long groupId,
            Long routineId,
            Long cursor,
            Integer size
    ) {
        groupValidationService.validateActiveGroupMember(groupId, memberId);

        if (!groupRoutineRepository.existsByIdAndGroupId(routineId, groupId)) {
            log.warn("그 그룹에 없는 루틴의 인증 목록을 요청했습니다. groupId={}, routineId={}",
                    groupId, routineId);
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_NOT_FOUND);
        }

        int appliedSize = clampSize(size);
        List<GroupRoutineVerification> rows = groupRoutineVerificationRepository
                .findByRoutineByCursor(routineId, groupId, cursor, Limit.of(appliedSize + 1));
        CursorPage<GroupRoutineVerification> page =
                sliceByCursor(rows, appliedSize, GroupRoutineVerification::getId);

        Map<Long, String> imageUrls = signImageUrls(
                page.rows(),
                GroupRoutineVerification::getId,
                GroupRoutineVerification::getImageUrl,
                MediaPurpose.GROUP_ROUTINE_VERIFICATION);

        return VerificationConverter.toGroupRoutineFeed(
                page.rows(), imageUrls, page.nextCursor(), page.hasNext());
    }

    /**
     * 이 페이지에 실릴 사진들의 서명 주소를 만든다.
     *
     * <p><b>여기 도달했다는 것은 접근 판정을 이미 통과했다는 뜻이다.</b> 서명은 그 판정의
     * 결과물이지 판정의 일부가 아니다.
     *
     * <p>건별로 부르지만 S3 왕복이 없다(서명은 로컬 계산). 배치로 묶을 이유가 없다.
     */
    private <T> Map<Long, String> signImageUrls(
            List<T> rows,
            Function<T, Long> idExtractor,
            Function<T, String> keyExtractor,
            MediaPurpose purpose
    ) {
        return rows.stream().collect(Collectors.toMap(
                idExtractor,
                row -> mediaService.resolveViewUrl(keyExtractor.apply(row), purpose)));
    }

    /** size + 1로 받아온 행에서 현재 페이지·다음 커서·다음 페이지 여부를 뽑아낸 결과. */
    private record CursorPage<T>(List<T> rows, Long nextCursor, boolean hasNext) {
    }

    /**
     * 커서 페이지네이션 공통 처리. 다음 커서는 이번 페이지 마지막 항목의 id다.
     * 더 없으면 null을 내려 클라이언트가 요청을 멈추게 한다(챌린지 피드와 같은 규약).
     */
    private static <T> CursorPage<T> sliceByCursor(
            List<T> rows, int size, Function<T, Long> idExtractor) {
        boolean hasNext = rows.size() > size;
        List<T> pageRows = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = (hasNext && !pageRows.isEmpty())
                ? idExtractor.apply(pageRows.get(pageRows.size() - 1))
                : null;
        return new CursorPage<>(pageRows, nextCursor, hasNext);
    }

    /** 없으면 기본값, 상한을 넘으면 잘라낸다. 챌린지 피드와 같은 규약을 쓴다. */
    private static int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
