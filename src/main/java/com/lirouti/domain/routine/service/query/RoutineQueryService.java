package com.lirouti.domain.routine.service.query;

import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.routine.converter.RoutineConverter;
import com.lirouti.global.util.TimeUtil;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.entity.RoutineTemplate;
import com.lirouti.domain.routine.exception.RoutineException;
import com.lirouti.domain.routine.exception.code.error.RoutineErrorCode;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import com.lirouti.domain.routine.repository.RoutineTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.netty.util.concurrent.FastThreadLocal.size;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineQueryService {
    private final MemberQueryService memberQueryService;
    private final RoutineCategoryRepository routineCategoryRepository;
    private final RoutineTemplateRepository routineTemplateRepository;
    private final MemberRoutineRepository memberRoutineRepository;
    private final RoutineCompletionSource completionSource;

    /**
     * 루틴 추가 화면의 카테고리 칩 목록을 조회한다.
     * 앱이 제공하는 고정 카테고리 전체와 요청 회원이 만든 사용자 카테고리를 함께 돌려준다.
     *
     * @param memberId 조회를 요청한 회원 ID
     * @return 카테고리 목록과 남은 추가 가능 개수
     */
    @Transactional(readOnly = true)
    public RoutineResDTO.CategoryList getCategories(Long memberId) {
        memberQueryService.getActiveMember(memberId);

        List<RoutineCategory> categories = routineCategoryRepository.findUsableByMemberId(memberId);
        long ownedCount = routineCategoryRepository.countByOwnerIdAndActiveTrue(memberId);
        int addableCount = (int) Math.max(
                0, RoutineCategory.MAX_MEMBER_CATEGORY_COUNT - ownedCount);

        log.debug("루틴 카테고리 목록을 조회했습니다. memberId={}, categoryCount={}, addableCount={}",
                memberId, categories.size(), addableCount);

        return RoutineConverter.toCategoryList(categories, addableCount);
    }

    /**
     * 기본 제공 루틴 목록을 조회한다. 카테고리를 지정하지 않으면 화면의 `전체` 탭에 해당하는
     * 모든 카테고리의 목록을 카테고리 순서대로 돌려준다.
     *
     * <p>회원이 이미 등록한 기본 루틴에는 {@code alreadyAdded}가 붙는다. 목록의 체크 상태를
     * 클라이언트가 다시 계산하지 않게 하려는 것이다.
     *
     * @param memberId 조회를 요청한 회원 ID
     * @param categoryId 조회할 카테고리 ID. {@code null}이면 전체
     * @return 기본 제공 루틴 목록
     * @throws RoutineException 지정한 카테고리를 찾을 수 없거나 사용할 수 없는 경우
     */
    @Transactional(readOnly = true)
    public RoutineResDTO.TemplateList getTemplates(Long memberId, Long categoryId) {
        memberQueryService.getActiveMember(memberId);

        List<RoutineTemplate> templates = categoryId == null
                ? routineTemplateRepository.findAllActiveWithCategory()
                : findTemplatesInCategory(memberId, categoryId);

        Set<Long> addedTemplateIds =
                new HashSet<>(memberRoutineRepository.findTemplateIdsByMemberId(memberId));

        log.debug("기본 제공 루틴 목록을 조회했습니다. memberId={}, categoryId={}, templateCount={}",
                memberId, categoryId, templates.size());

        return RoutineConverter.toTemplateList(templates, addedTemplateIds);
    }

    /**
     * 회원이 사용할 수 있는 카테고리인지 확인한 뒤 그 카테고리의 기본 제공 루틴을 조회한다.
     * 사용자 카테고리에는 기본 제공 루틴이 없으므로 결과가 비어 있을 수 있다.
     *
     * @param memberId 조회를 요청한 회원 ID
     * @param categoryId 조회할 카테고리 ID
     * @return 해당 카테고리의 활성 기본 제공 루틴 목록
     * @throws RoutineException 카테고리를 찾을 수 없거나 다른 회원의 카테고리인 경우
     */
    private List<RoutineTemplate> findTemplatesInCategory(Long memberId, Long categoryId) {
        RoutineCategory category = routineCategoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> {
                    log.warn("활성 루틴 카테고리 조회에 실패했습니다. memberId={}, categoryId={}",
                            memberId, categoryId);
                    return new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
                });

        if (!category.isUsableBy(memberId)) {
            log.warn("다른 회원의 카테고리 조회를 차단했습니다. memberId={}, categoryId={}",
                    memberId, categoryId);
            throw new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_ACCESS_DENIED);
        }

        return routineTemplateRepository.findActiveByCategoryId(categoryId);
    }

    /**
     * 홈 화면 '오늘의 루틴' 탭에서 참조할 데이터
     * 오늘 반복 요일에 해당하는 회원의 활성 개인 루틴을 조회한다.
     *
     * @param memberId: 조회를 요청한 회원 ID
     * @return 마감 시각 순으로 정렬된 오늘의 개인 루틴 목록
     */
    @Transactional(readOnly = true)
    public List<RoutineResDTO.Routine> getTodayRoutines(Long memberId) {
        memberQueryService.getActiveMember(memberId);

        LocalDate todayDate = LocalDate.now(TimeUtil.KST);
        DayOfWeek today = todayDate.getDayOfWeek();
        List<MemberRoutine> routines = memberRoutineRepository.findTodayActiveByMemberId(memberId, today);

        // 루틴마다 "오늘 인증했나"를 따로 물으면 목록 크기만큼 쿼리가 나간다. 한 번에 받아 맞춘다.
        Set<Long> completed = completionSource.findCompletedRoutineIds(
                routines.stream().map(MemberRoutine::getId).toList(), todayDate);

        log.debug("오늘의 개인 루틴 목록을 조회했습니다. memberId={}, today={}, routineCount={}, completed={}",
                memberId, today, routines.size(), completed.size());

        return RoutineConverter.toRoutineList(routines, completed);
    }
}
