package com.lirouti.domain.notification.repository;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.notification.entity.FcmDevice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("FCM 기기 토큰 저장소 테스트")
class FcmDeviceRepositoryTest {
    @Autowired
    private FcmDeviceRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 토큰을 재등록하면 행을 늘리지 않고 마지막 회원에게 재귀속한다")
    void upsertActive_SameToken_ReassignsSingleRow() {
        Member first = persistMember("first");
        Member second = persistMember("second");
        LocalDateTime firstRegistration = LocalDateTime.of(2026, 8, 11, 12, 0);
        LocalDateTime secondRegistration = firstRegistration.plusMinutes(5);

        repository.upsertActive(first.getId(), "same-token", firstRegistration);
        repository.upsertActive(second.getId(), "same-token", secondRegistration);
        entityManager.flush();
        entityManager.clear();

        FcmDevice device = repository.findByToken("same-token").orElseThrow();
        assertThat(repository.count()).isEqualTo(1L);
        assertThat(device.getMember().getId()).isEqualTo(second.getId());
        assertThat(device.isActive()).isTrue();
        assertThat(device.getLastRegisteredAt()).isEqualTo(secondRegistration);
        assertThat(device.getDeactivatedAt()).isNull();
    }

    @Test
    @DisplayName("이전 회원의 늦은 해제 요청은 재귀속된 토큰을 비활성화하지 않는다")
    void deactivateOwnedToken_PreviousOwner_DoesNotDeactivateReassignedToken() {
        Member first = persistMember("old-owner");
        Member second = persistMember("new-owner");
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 0);
        repository.upsertActive(first.getId(), "reassigned-token", now);
        repository.upsertActive(second.getId(), "reassigned-token", now.plusMinutes(1));

        int updated = repository.deactivateOwnedToken(
                first.getId(),
                "reassigned-token",
                now.plusMinutes(2)
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isZero();
        assertThat(repository.findByToken("reassigned-token").orElseThrow().isActive()).isTrue();
    }

    private Member persistMember(String suffix) {
        Member member = Member.builder()
                .email("fcm-" + suffix + "@example.com")
                .nickname("FCM" + suffix)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("fcm-social-" + suffix)
                .build();
        entityManager.persist(member);
        entityManager.flush();
        return member;
    }
}
