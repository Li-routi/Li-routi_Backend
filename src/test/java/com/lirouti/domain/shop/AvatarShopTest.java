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
import com.lirouti.domain.shop.exception.ShopInsufficientBalanceException;
import com.lirouti.domain.shop.exception.ShopInsufficientBalanceException.Shortage;
import com.lirouti.domain.shop.exception.ShopItemRejectedException;
import com.lirouti.domain.shop.exception.code.error.ShopErrorCode;
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
import static org.assertj.core.api.Assertions.tuple;
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
    @DisplayName("재화가 섞여도 한 번에 산다 — 재화별로 나눠 빠진다")
    void purchase_SplitsDeductionPerCurrency() {
        giveBalance(Currency.TOPAZ, 1000);
        giveBalance(Currency.GEM, 1000);

        // 모자(TOPAZ 300) + 텀블러(TOPAZ 300) + 티셔츠(GEM 500)
        ShopResDTO.PurchaseResult result = shopCommandService.purchase(
                me.getId(), List.of(hat.getId(), tumbler.getId(), shirt.getId()), "buy-1");
        em.flush();

        assertAll(
                () -> assertThat(balance(Currency.TOPAZ)).isEqualTo(400),
                () -> assertThat(balance(Currency.GEM)).isEqualTo(500),
                () -> assertThat(result.purchasedItemIds())
                        .as("요청한 순서 그대로 내린다")
                        .containsExactly(hat.getId(), tumbler.getId(), shirt.getId()),
                () -> assertThat(result.payments())
                        .as("재화마다 한 줄이다")
                        .extracting(ShopResDTO.Payment::currency, ShopResDTO.Payment::paidAmount)
                        .containsExactlyInAnyOrder(
                                tuple(Currency.TOPAZ, 600), tuple(Currency.GEM, 500)),
                () -> assertThat(memberAvatarItemRepository
                        .findOwnedItemIdsIn(me.getId(),
                                List.of(hat.getId(), tumbler.getId(), shirt.getId())))
                        .hasSize(3)
        );
    }

    @Test
    @DisplayName("사도 입히지 않는다 — 같은 자리를 둘 사면 어느 쪽을 입힐지 정할 수 없다")
    void purchase_DoesNotEquip() {
        giveBalance(Currency.TOPAZ, 1000);

        shopCommandService.purchase(me.getId(), List.of(hat.getId(), tumbler.getId()), "buy-1");
        em.flush();

        assertThat(equippedSlots())
                .as("착용은 착장 저장이 맡는다").isEmpty();
    }

    @Test
    @DisplayName("하나라도 이미 가졌으면 전체를 거절한다 — 어떤 것인지 함께 알린다")
    void purchase_RejectsAllWhenOneIsOwned() {
        giveBalance(Currency.TOPAZ, 1000);
        own(hat);

        assertThatThrownBy(() -> shopCommandService.purchase(
                me.getId(), List.of(hat.getId(), tumbler.getId()), "buy-1"))
                .isInstanceOf(ShopItemRejectedException.class)
                .satisfies(e -> assertAll(
                        () -> assertThat(((ShopItemRejectedException) e).getCode())
                                .isEqualTo(ShopErrorCode.ALREADY_OWNED),
                        () -> assertThat(((ShopItemRejectedException) e).getItemIds())
                                .as("화면이 이것만 빼고 다시 시도할 수 있어야 한다")
                                .containsExactly(hat.getId())));

        assertThat(balance(Currency.TOPAZ))
                .as("막힌 요청은 아무것도 빼지 않는다").isEqualTo(1000);
    }

    @Test
    @DisplayName("모자란 재화를 전부 알린다 — 하나씩 알리면 충전하고 와서 또 막힌다")
    void purchase_ReportsEveryShortage() {
        giveBalance(Currency.TOPAZ, 100);   // 모자 300 에 200 모자람
        giveBalance(Currency.GEM, 200);     // 티셔츠 500 에 300 모자람

        assertThatThrownBy(() -> shopCommandService.purchase(
                me.getId(), List.of(hat.getId(), shirt.getId()), "buy-1"))
                .isInstanceOf(ShopInsufficientBalanceException.class)
                .satisfies(e -> assertThat(
                        ((ShopInsufficientBalanceException) e).getShortages())
                        .extracting(Shortage::currency, Shortage::required,
                                Shortage::balance, Shortage::shortfall)
                        .containsExactlyInAnyOrder(
                                tuple(Currency.TOPAZ, 300, 100, 200),
                                tuple(Currency.GEM, 500, 200, 300)));

        assertAll(
                () -> assertThat(balance(Currency.TOPAZ)).isEqualTo(100),
                () -> assertThat(balance(Currency.GEM)).isEqualTo(200)
        );
    }

    @Test
    @DisplayName("한 재화만 모자라도 전체가 막힌다 — 묶음은 전부 되거나 전부 안 되거나다")
    void purchase_RejectsWholeBundleWhenOneCurrencyIsShort() {
        giveBalance(Currency.TOPAZ, 1000);  // 모자는 살 수 있다
        giveBalance(Currency.GEM, 100);     // 티셔츠 500 은 못 산다

        assertThatThrownBy(() -> shopCommandService.purchase(
                me.getId(), List.of(hat.getId(), shirt.getId()), "buy-1"))
                .isInstanceOf(ShopInsufficientBalanceException.class);

        assertThat(balance(Currency.TOPAZ))
                .as("살 수 있었던 쪽도 빠지지 않는다").isEqualTo(1000);
    }

    @Test
    @DisplayName("판매가 종료된 아이템은 살 수 없다 — 목록에 없어도 id 로 부를 수 있다")
    void purchase_RejectsWhenNotOnSale() {
        giveBalance(Currency.TOPAZ, 1000);
        deactivate(hat);

        assertThatThrownBy(() -> shopCommandService.purchase(
                me.getId(), List.of(hat.getId()), "buy-1"))
                .isInstanceOf(ShopItemRejectedException.class)
                .satisfies(e -> assertThat(((ShopItemRejectedException) e).getCode())
                        .isEqualTo(ShopErrorCode.ITEM_NOT_ON_SALE));

        assertThat(balance(Currency.TOPAZ)).isEqualTo(1000);
    }

    @Test
    @DisplayName("같은 아이템을 두 번 담으면 거절한다 — 말없이 합치면 본 금액과 어긋난다")
    void purchase_RejectsDuplicateItem() {
        giveBalance(Currency.TOPAZ, 1000);

        assertThatThrownBy(() -> shopCommandService.purchase(
                me.getId(), List.of(hat.getId(), hat.getId()), "buy-1"))
                .isInstanceOf(ShopItemRejectedException.class)
                .satisfies(e -> assertAll(
                        () -> assertThat(((ShopItemRejectedException) e).getCode())
                                .isEqualTo(ShopErrorCode.DUPLICATE_ITEM),
                        () -> assertThat(((ShopItemRejectedException) e).getItemIds())
                                .containsExactly(hat.getId())));

        assertThat(balance(Currency.TOPAZ)).isEqualTo(1000);
    }

    @Test
    @DisplayName("없는 아이템은 어떤 id 인지 알린다")
    void purchase_ReportsMissingItemIds() {
        giveBalance(Currency.TOPAZ, 1000);
        long missing = hat.getId() + 100_000L;

        assertThatThrownBy(() -> shopCommandService.purchase(
                me.getId(), List.of(hat.getId(), missing), "buy-1"))
                .isInstanceOf(ShopItemRejectedException.class)
                .satisfies(e -> assertAll(
                        () -> assertThat(((ShopItemRejectedException) e).getCode())
                                .isEqualTo(ShopErrorCode.ITEM_NOT_FOUND),
                        () -> assertThat(((ShopItemRejectedException) e).getItemIds())
                                .containsExactly(missing)));
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
