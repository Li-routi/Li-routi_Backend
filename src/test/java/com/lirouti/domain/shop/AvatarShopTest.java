package com.lirouti.domain.shop;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.exception.ShopException;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import com.lirouti.domain.shop.service.command.ShopCommandService;
import com.lirouti.domain.shop.service.query.ShopQueryService;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.exception.WalletException;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 아바타 아이템의 구매와 착용.
 *
 * <p>돈이 오가는 자리라 <b>중복 구매와 미보유 착용</b>을 특히 본다. 둘 다 뚫리면 재화가 두 번
 * 빠지거나 구매를 건너뛸 수 있다.
 */
@SpringBootTest
@Transactional
@DisplayName("아바타 상점")
class AvatarShopTest {

    @Autowired
    private ShopCommandService shopCommandService;
    @Autowired
    private ShopQueryService shopQueryService;
    @Autowired
    private MemberAvatarItemRepository memberAvatarItemRepository;
    @Autowired
    private MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    @Autowired
    private MemberWalletRepository memberWalletRepository;
    @Autowired
    private WalletService walletService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private AvatarItem hat;      // HEAD · TOPAZ 300
    private AvatarItem tumbler;  // HAND · TOPAZ 300
    private AvatarItem shirt;    // BODY · GEM   500

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("av" + n + "@ex.com").nickname("av" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("av-sid-" + n).build();
        em.persist(me);

        hat = item(AvatarSlot.HEAD, Currency.TOPAZ, 300, "모자");
        tumbler = item(AvatarSlot.HAND, Currency.TOPAZ, 300, "텀블러");
        shirt = item(AvatarSlot.BODY, Currency.GEM, 500, "티셔츠");
        em.flush();
    }

    // ── 픽스처 ──

    private AvatarItem item(AvatarSlot slot, Currency currency, int price, String name) {
        AvatarItem item = AvatarItem.builder()
                .slot(slot).currency(currency).price(price)
                .name(name).imageKey("avatar/item/" + name + "-v1.png")
                .sortOrder(1).active(true).build();
        em.persist(item);
        return item;
    }

    private void giveBalance(Currency currency, int amount) {
        walletService.grant(new WalletCommand(me.getId(), currency,
                WalletTransactionType.CHALLENGE_REWARD,
                "seed-" + currency + "-" + seq.get(), null, null), 0, amount);
        em.flush();
    }

    private int balance(Currency currency) {
        return memberWalletRepository.findByMemberIdAndCurrency(me.getId(), currency)
                .map(w -> w.totalBalance()).orElse(0);
    }

    private void deactivate(AvatarItem item) {
        em.createQuery("update AvatarItem i set i.active = false where i.id = :id")
                .setParameter("id", item.getId()).executeUpdate();
        em.clear();
    }

    private void own(AvatarItem item) {
        em.persist(MemberAvatarItem.builder()
                .member(me).avatarItem(item)
                .currency(item.getCurrency()).paidPrice(item.getPrice())
                .purchasedAt(LocalDateTime.now()).build());
        em.flush();
    }

    private List<AvatarSlot> equippedSlots() {
        return memberAvatarEquipmentRepository.findAllByMemberId(me.getId())
                .stream().map(e -> e.getSlot()).sorted().toList();
    }

    @Test
    @DisplayName("여러 회원의 장착 아이템을 아이템과 함께 한 번에 회원·슬롯 순서로 조회한다")
    void findAllByMemberIdInWithMemberAndAvatarItem_ReturnsOrderedEquipment() {
        Member other = Member.builder()
                .email("other" + seq.get() + "@ex.com").nickname("other")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("other-sid-" + seq.get()).build();
        em.persist(other);
        em.persist(MemberAvatarEquipment.builder().member(me).avatarItem(hat).build());
        em.persist(MemberAvatarEquipment.builder().member(me).avatarItem(tumbler).build());
        em.persist(MemberAvatarEquipment.builder().member(other).avatarItem(shirt).build());
        em.flush();
        em.clear();

        List<MemberAvatarEquipment> result = memberAvatarEquipmentRepository
                .findAllByMemberIdInWithMemberAndAvatarItem(List.of(me.getId(), other.getId()));

        assertThat(result).extracting(equipment -> equipment.getMember().getId())
                .containsExactly(me.getId(), me.getId(), other.getId());
        assertThat(result).extracting(MemberAvatarEquipment::getSlot)
                .containsExactly(AvatarSlot.HAND, AvatarSlot.HEAD, AvatarSlot.BODY);
        assertThat(result).extracting(equipment -> equipment.getAvatarItem().getImageKey())
                .containsExactly("avatar/item/텀블러-v1.png", "avatar/item/모자-v1.png",
                        "avatar/item/티셔츠-v1.png");
    }

    // ── 구매 ──

    @Test
    @DisplayName("구매하면 재화가 빠지고 보유·착용이 함께 남는다")
    void purchase_DeductsOwnsAndEquips() {
        giveBalance(Currency.TOPAZ, 1000);

        ShopResDTO.Avatar result = shopCommandService.purchase(me.getId(), hat.getId());
        em.flush();

        assertAll(
                () -> assertThat(balance(Currency.TOPAZ)).isEqualTo(700),
                () -> assertThat(memberAvatarItemRepository
                        .existsByMemberIdAndAvatarItemId(me.getId(), hat.getId())).isTrue(),
                () -> assertThat(result.equipped()).hasSize(1),
                () -> assertThat(result.equipped().getFirst().slot()).isEqualTo(AvatarSlot.HEAD)
        );
    }

    @Test
    @DisplayName("이미 가진 아이템은 다시 살 수 없다 — 재화도 빠지지 않는다")
    void purchase_RejectsWhenAlreadyOwned() {
        giveBalance(Currency.TOPAZ, 1000);
        shopCommandService.purchase(me.getId(), hat.getId());
        em.flush();

        assertThatThrownBy(() -> shopCommandService.purchase(me.getId(), hat.getId()))
                .isInstanceOf(ShopException.class);

        assertThat(balance(Currency.TOPAZ))
                .as("두 번째 시도에서 더 빠지지 않는다").isEqualTo(700);
    }

    @Test
    @DisplayName("잔액이 모자라면 살 수 없다")
    void purchase_RejectsWhenInsufficientBalance() {
        giveBalance(Currency.TOPAZ, 100);   // 모자 300 보다 적다

        // 회수 테스트와 같은 이유로, 여기서 "보유 행이 안 남는다" 까지는 보지 않는다 —
        // 테스트가 @Transactional 이라 서비스가 바깥 트랜잭션에 참여해 실제 롤백 경계가
        // 만들어지지 않는다. 잔액이 안 빠지는 것으로 차감이 막혔음을 본다.
        assertThatThrownBy(() -> shopCommandService.purchase(me.getId(), hat.getId()))
                .isInstanceOf(WalletException.class);

        assertThat(balance(Currency.TOPAZ)).isEqualTo(100);
    }

    @Test
    @DisplayName("판매가 종료된 아이템은 살 수 없다 — 목록에 없어도 id 로 부를 수 있다")
    void purchase_RejectsWhenNotOnSale() {
        giveBalance(Currency.TOPAZ, 1000);
        deactivate(hat);

        assertThatThrownBy(() -> shopCommandService.purchase(me.getId(), hat.getId()))
                .isInstanceOf(ShopException.class);

        assertThat(balance(Currency.TOPAZ)).isEqualTo(1000);
    }

    @Test
    @DisplayName("구매는 그 자리만 바꾼다 — 손에 든 것을 샀다고 모자가 벗겨지지 않는다")
    void purchase_TouchesOnlyItsOwnSlot() {
        giveBalance(Currency.TOPAZ, 1000);
        shopCommandService.purchase(me.getId(), hat.getId());       // HEAD
        shopCommandService.purchase(me.getId(), tumbler.getId());   // HAND
        em.flush();

        assertThat(equippedSlots())
                .as("먼저 산 모자가 그대로 남아 있다")
                .containsExactly(AvatarSlot.HEAD, AvatarSlot.HAND);
    }

    // ── 착용 ──

    @Test
    @DisplayName("요청에 없는 자리는 벗겨진다 — 보낸 것이 곧 전체 착장이다")
    void equip_UnequipsSlotsNotInRequest() {
        own(hat);
        own(tumbler);
        shopCommandService.equip(me.getId(), List.of(hat.getId(), tumbler.getId()));
        em.flush();

        shopCommandService.equip(me.getId(), List.of(hat.getId()));
        em.flush();

        assertThat(equippedSlots())
                .as("HAND 는 보내지 않았으므로 벗는다")
                .containsExactly(AvatarSlot.HEAD);
    }

    @Test
    @DisplayName("빈 목록을 보내면 전부 벗는다")
    void equip_EmptyListUnequipsAll() {
        own(hat);
        shopCommandService.equip(me.getId(), List.of(hat.getId()));
        em.flush();

        shopCommandService.equip(me.getId(), List.of());
        em.flush();

        assertThat(equippedSlots()).isEmpty();
    }

    @Test
    @DisplayName("하나라도 보유하지 않았으면 전체를 거절한다 — 구매를 건너뛰는 길이 된다")
    void equip_RejectsAllWhenAnyNotOwned() {
        own(hat);
        shopCommandService.equip(me.getId(), List.of(hat.getId()));
        em.flush();

        assertThatThrownBy(() ->
                shopCommandService.equip(me.getId(), List.of(hat.getId(), tumbler.getId())))
                .isInstanceOf(ShopException.class);

        assertThat(equippedSlots())
                .as("거절됐으므로 착용은 그대로다").containsExactly(AvatarSlot.HEAD);
    }

    @Test
    @DisplayName("같은 자리에 두 개를 보내면 거절한다")
    void equip_RejectsDuplicateSlot() {
        AvatarItem otherHat = item(AvatarSlot.HEAD, Currency.TOPAZ, 300, "다른모자");
        em.flush();
        own(hat);
        own(otherHat);

        assertThatThrownBy(() ->
                shopCommandService.equip(me.getId(), List.of(hat.getId(), otherHat.getId())))
                .isInstanceOf(ShopException.class);
    }

    @Test
    @DisplayName("판매가 종료돼도 이미 산 것은 계속 입을 수 있다")
    void equip_AllowsDiscontinuedItemAlreadyOwned() {
        own(hat);
        deactivate(hat);

        shopCommandService.equip(me.getId(), List.of(hat.getId()));
        em.flush();

        assertThat(equippedSlots()).containsExactly(AvatarSlot.HEAD);
    }

    // ── 목록 ──

    /**
     * DB 에는 key 를 담고 응답에는 주소를 내린다. 이 둘이 같아지면 앱은 key 를 이미지 주소로
     * 알고 그리려다 실패한다 — 조립이 빠졌다는 신호다.
     */
    @Test
    @DisplayName("목록은 저장된 key 가 아니라 조립된 주소를 내린다")
    void items_ReturnsResolvedUrlNotKey() {
        ShopResDTO.Items items = shopQueryService.getItems(me.getId(), AvatarSlot.HEAD, false);

        assertThat(items.items()).filteredOn(item -> item.id().equals(hat.getId()))
                .singleElement()
                .satisfies(item -> assertAll(
                        () -> assertThat(item.imageUrl()).startsWith("http"),
                        () -> assertThat(item.imageUrl()).endsWith(hat.getImageKey()),
                        () -> assertThat(item.imageUrl())
                                .as("key 를 그대로 내리면 앱이 그리지 못한다")
                                .isNotEqualTo(hat.getImageKey())));
    }

    @Test
    @DisplayName("판매가 종료돼도 보유한 것은 목록에 남는다 — 빠지면 조용히 벗겨진다")
    void items_KeepsOwnedItemAfterDiscontinued() {
        own(hat);
        deactivate(hat);

        ShopResDTO.Items items = shopQueryService.getItems(me.getId(), null, false);

        assertAll(
                () -> assertThat(items.items()).extracting(ShopResDTO.Item::id)
                        .contains(hat.getId()),
                () -> assertThat(items.items()).filteredOn(i -> i.id().equals(hat.getId()))
                        .singleElement()
                        .satisfies(i -> assertAll(
                                () -> assertThat(i.owned()).isTrue(),
                                () -> assertThat(i.onSale())
                                        .as("보유했지만 판매는 끝났다").isFalse()))
        );
    }

    @Test
    @DisplayName("자리를 지정하면 그 자리만, 생략하면 전체가 나온다")
    void items_FiltersBySlot() {
        ShopResDTO.Items head = shopQueryService.getItems(me.getId(), AvatarSlot.HEAD, false);
        ShopResDTO.Items all = shopQueryService.getItems(me.getId(), null, false);

        assertAll(
                () -> assertThat(head.items()).isNotEmpty(),
                () -> assertThat(head.items()).allMatch(i -> i.slot() == AvatarSlot.HEAD),
                () -> assertThat(all.items().size()).isGreaterThan(head.items().size())
        );
    }

    @Test
    @DisplayName("보유한 것만 보기")
    void items_OwnedOnly() {
        own(hat);

        ShopResDTO.Items owned = shopQueryService.getItems(me.getId(), null, true);

        assertAll(
                () -> assertThat(owned.items()).extracting(ShopResDTO.Item::id)
                        .containsExactly(hat.getId()),
                () -> assertThat(owned.items()).allMatch(ShopResDTO.Item::owned)
        );
    }
}
