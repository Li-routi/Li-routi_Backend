package com.lirouti.domain.achievement.service;

import java.util.List;

/**
 * 그룹의 현재 활성 구성원이 누구인지 답하는 쪽.
 *
 * <p>{@code RoutineCompletionSource}(루틴 도메인이 소유, 인증 도메인이 구현)와 정확히 같은
 * 이유로 이 인터페이스는 achievement 도메인이 소유하고 구현은 group 도메인이 한다 — achievement가
 * 그룹 멤버십의 내부 구조({@code GroupMember}, {@code GroupMemberStatus} 등)를 직접 알면
 * 두 도메인이 서로를 참조하게 된다. 방향을 achievement → group 한 방향으로만 흐르게 한다.
 *
 * <p>그룹 단위 업적(GROUP_CUMULATIVE_COUNT·GROUP_DISTINCT_DAY_COUNT)이 목표치에 도달하는
 * 순간, 그 시점의 활성 구성원 전원에게 개별 {@code MemberAchievement} 를 만들어 달성 처리하는
 * 데 쓴다 — "계정당 1회 달성"이 회원 단위 개념이라, 그룹 카운터가 목표를 채웠다고 그룹 자체가
 * 뭔가를 받는 게 아니라 그 순간의 구성원 각자가 받아야 하기 때문이다.
 */
public interface GroupActiveMemberSource {

    /**
     * @param groupId 조회할 그룹 ID
     * @return 그 그룹의 현재 활성(ACTIVE) 구성원 회원 ID 목록
     */
    List<Long> findActiveMemberIds(Long groupId);
}
