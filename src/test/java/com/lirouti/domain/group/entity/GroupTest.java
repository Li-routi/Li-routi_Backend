package com.lirouti.domain.group.entity;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Group 테스트")
class GroupTest {

    @Test
    @DisplayName("신규 그룹은 잠금 해제 상태로 생성된다")
    void create_DefaultsToUnlocked() {
        // when
        Group group = group();

        // then
        assertThat(group.isLocked()).isFalse();
        assertThat(group.getLastVerificationAt()).isNull();
    }

    @Test
    @DisplayName("잠금과 잠금 해제는 초대코드를 변경하지 않는다")
    void lockAndUnlock_PreserveInviteCode() {
        // given
        Group group = group();

        // when
        group.lock();

        // then
        assertThat(group.isLocked()).isTrue();
        assertThat(group.getInviteCode()).isEqualTo("LOCK001");

        // when
        group.unlock();

        // then
        assertThat(group.isLocked()).isFalse();
        assertThat(group.getInviteCode()).isEqualTo("LOCK001");
    }

    @Test
    @DisplayName("최근 인증 등록 시각은 더 최신 저장 시각으로만 갱신한다")
    void updateLastVerificationAt_KeepsLatestValue() {
        Group group = group();
        LocalDateTime first = LocalDateTime.of(2026, 8, 18, 9, 0);
        LocalDateTime latest = LocalDateTime.of(2026, 8, 18, 10, 0);

        group.updateLastVerificationAt(latest);
        group.updateLastVerificationAt(first);
        group.updateLastVerificationAt(null);

        assertThat(group.getLastVerificationAt()).isEqualTo(latest);
    }

    private Group group() {
        return Group.builder()
                .name("잠금 테스트 그룹")
                .inviteCode("LOCK001")
                .build();
    }
}
