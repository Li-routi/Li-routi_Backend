package com.lirouti.domain.shop;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.repository.AvatarItemRepository;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import com.lirouti.domain.shop.service.command.ShopCommandService;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.repository.WalletTransactionRepository;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 같은 아이템을 동시에 사도 한 번만 사진다.
 *
 * <p><b>여기가 뚫리면 재화가 두 번 빠진다.</b> 영구 보유라 두 번 사도 얻는 것이 없으므로
 * 사용자는 그대로 손해다. 이 저장소는 "검사하고 저장" 사이에 동시 요청이 모두 통과하는 것을
 * 이미 겪었다(챌린지 참여·인증).
 *
 * <p>{@code @Transactional} 을 쓰지 않는다 — 스레드들이 각자 트랜잭션으로 커밋된 같은 행을
 * 두고 경합해야 한다.
 */
@SpringBootTest
@DisplayName("아바타 구매 동시성")
class AvatarPurchaseConcurrencyTest {

    private static final int PRICE = 300;
    private static final int BALANCE = 3000;   // 넉넉히 준다. 잔액이 아니라 중복이 막는지를 본다
    private static final int THREADS = 8;

    @Autowired
    private ShopCommandService shopCommandService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AvatarItemRepository avatarItemRepository;
    @Autowired
    private MemberAvatarItemRepository memberAvatarItemRepository;
    @Autowired
    private MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    @Autowired
    private MemberWalletRepository memberWalletRepository;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;
    @Autowired
    private WalletService walletService;

    private Long memberId;
    private Long itemId;
    /** 이 테스트가 만든 아이템만 지운다. 이름으로 지우면 다른 테스트 것까지 가져간다. */
    private final List<Long> createdItemIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 앞선 실행이 정리에 실패해 행을 남기면 유니크 제약에 걸린다. 실행마다 다른 값을 쓴다.
        String tag = "avconc-" + System.nanoTime();
        Member m = memberRepository.save(Member.builder()
                .email(tag + "@ex.com").nickname(tag)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag).build());
        memberId = m.getId();

        AvatarItem item = avatarItemRepository.save(AvatarItem.builder()
                .slot(AvatarSlot.HAND).currency(Currency.TOPAZ).price(PRICE)
                .name("동시성 아이템").imageUrl("https://img/conc")
                .sortOrder(1).active(true).build());
        itemId = item.getId();
        createdItemIds.add(itemId);

        walletService.grant(new WalletCommand(memberId, Currency.TOPAZ,
                WalletTransactionType.CHALLENGE_REWARD, "avconc-seed-" + memberId, null, null), 0, BALANCE);
    }

    @AfterEach
    void tearDown() {
        // 파생 삭제 쿼리(deleteAllByMemberId)는 트랜잭션 안에서만 돈다. 이 테스트는 스레드
        // 경합을 보려고 @Transactional 을 쓰지 않으므로 조회 후 삭제로 정리한다.
        memberAvatarEquipmentRepository.deleteAll(
                memberAvatarEquipmentRepository.findAllByMemberId(memberId));
        memberAvatarItemRepository.deleteAll(memberAvatarItemRepository.findAll().stream()
                .filter(mi -> mi.getMember().getId().equals(memberId)).toList());
        walletTransactionRepository.deleteAll(walletTransactionRepository.findAll().stream()
                .filter(t -> t.getMember().getId().equals(memberId)).toList());
        memberWalletRepository.findByMemberIdAndCurrency(memberId, Currency.TOPAZ)
                .ifPresent(memberWalletRepository::delete);
        avatarItemRepository.deleteAllById(createdItemIds);
        createdItemIds.clear();
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("같은 아이템을 동시에 사도 한 번만 사지고 재화도 한 번만 빠진다")
    void concurrentPurchase_ChargesOnce() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    shopCommandService.purchase(memberId, itemId);
                    success.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    // 애플리케이션 분기(ALREADY_OWNED)든 유니크 제약이든 거절이면 된다.
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        long owned = memberAvatarItemRepository.findOwnedItemIds(memberId).size();
        int balance = memberWalletRepository
                .findByMemberIdAndCurrency(memberId, Currency.TOPAZ)
                .map(w -> w.totalBalance()).orElse(0);

        assertAll(
                () -> assertThat(workersReady).isTrue(),
                () -> assertThat(finished).isTrue(),
                () -> assertThat(success.get()).as("성공은 정확히 한 번").isEqualTo(1),
                () -> assertThat(rejected.get()).isEqualTo(THREADS - 1),
                () -> assertThat(owned).as("보유 행도 하나뿐").isEqualTo(1),
                () -> assertThat(balance)
                        .as("재화는 한 번만 빠진다 — 여기가 뚫리면 그대로 손해다")
                        .isEqualTo(BALANCE - PRICE)
        );
    }

    /**
     * 동시에 착장을 저장해도 <b>어느 쪽도 보내지 않은 조합</b>이 남지 않는다.
     *
     * <pre>
     * T1 delete all           T2 delete all
     * T1 insert HEAD          T2 insert BODY
     * → HEAD + BODY           둘 다 "이것만 입겠다" 고 보냈는데 둘 다 입었다
     * </pre>
     *
     * <p>각 요청이 "이 하나만 입겠다" 이므로 <b>최종 상태는 반드시 한 벌</b>이어야 한다.
     *
     * <p><b>이 테스트는 결과를 보지 어느 장치가 지켰는지는 가르지 못한다.</b> 회원 잠금을 빼고
     * 세 번 돌려도 통과했다 — {@code DELETE ... WHERE member_id = ?} 가 이미 행을 잠가
     * 사실상 직렬화되기 때문으로 보인다. 잠금은 그 위에 얹은 방어이고, <b>MySQL 의 삭제 잠금
     * 동작에 기대지 않으려고 남긴다.</b> 격리 수준이나 삭제 방식이 바뀌면 그 성질은 사라진다.
     */
    @Test
    @DisplayName("동시에 착장을 저장해도 섞이지 않는다 — 어느 쪽도 보내지 않은 조합이 남지 않는다")
    void concurrentEquip_NeverMixesOutfits() throws InterruptedException {
        AvatarSlot[] slots = {AvatarSlot.HEAD, AvatarSlot.BODY, AvatarSlot.HAND};
        Long[] itemIds = new Long[slots.length];
        for (int i = 0; i < slots.length; i++) {
            AvatarItem item = avatarItemRepository.save(AvatarItem.builder()
                    .slot(slots[i]).currency(Currency.TOPAZ).price(PRICE)
                    .name("동시착용" + i).imageUrl("https://img/eq" + i)
                    .sortOrder(1).active(true).build());
            itemIds[i] = item.getId();
            createdItemIds.add(item.getId());
            shopCommandService.purchase(memberId, item.getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Long only = itemIds[i % itemIds.length];
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    shopCommandService.equip(memberId, List.of(only));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                    // 경합에 져 실패하는 것은 괜찮다. 섞이지 않는 것만 본다.
                } finally {
                    done.countDown();
                }
            });
        }

        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        int equippedCount = memberAvatarEquipmentRepository.findAllByMemberId(memberId).size();

        assertAll(
                () -> assertThat(workersReady).isTrue(),
                () -> assertThat(finished).isTrue(),
                () -> assertThat(equippedCount)
                        .as("모든 요청이 한 벌만 보냈으므로 최종도 한 벌이어야 한다")
                        .isEqualTo(1)
        );
    }
}
