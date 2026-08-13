package com.lirouti.domain.badge.service;

import com.lirouti.domain.badge.entity.Badge;
import com.lirouti.domain.badge.repository.BadgeRepository;
import com.lirouti.domain.badge.repository.MemberBadgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeGrantService {

    private final BadgeRepository badgeRepository;
    private final MemberBadgeRepository memberBadgeRepository;

    /**
     * badgeCode에 해당하는 배지를 지급한다. 이미 보유 중이면 아무 일도 하지 않는다(멱등).
     */
    @Transactional
    public void grant(Long memberId, String badgeCode) {
        Badge badge = badgeRepository.findByCode(badgeCode)
                .orElseThrow(() -> {
                    log.error("존재하지 않는 배지 코드입니다. badgeCode={}", badgeCode);
                    return new IllegalStateException("존재하지 않는 배지 코드: " + badgeCode);
                });
        memberBadgeRepository.insertIfAbsent(memberId, badge.getId());
    }
}