package com.lirouti.domain.shop.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.shop.converter.ShopConverter;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.entity.MemberAvatarItem;
import com.lirouti.domain.shop.entity.AvatarPurchase;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.exception.ShopException;
import com.lirouti.domain.shop.exception.ShopInsufficientBalanceException;
import com.lirouti.domain.shop.exception.ShopItemRejectedException;
import com.lirouti.domain.shop.exception.code.error.ShopErrorCode;
import com.lirouti.domain.shop.repository.AvatarItemRepository;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import com.lirouti.domain.shop.repository.AvatarPurchaseRepository;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.service.WalletResult;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import com.lirouti.global.util.TimeUtil;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.character.service.query.AvatarLayerAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 아바타 아이템의 구매와 착용.
 *
 * <p><b>둘 다 회원 행을 먼저 잠근다.</b> 구매도 착용을 바꾸므로, 저장과 동시에 들어오면 같은
 * 슬롯을 서로 덮는다. 잠글 부모 행이 따로 없어 {@code member} 를 잠근다 — 아바타 변경은 드문
 * 요청이라 이 잠금이 경합을 만들 일은 거의 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopCommandService {

    private final MemberRepository memberRepository;
    private final AvatarItemRepository avatarItemRepository;
    private final MemberAvatarItemRepository memberAvatarItemRepository;
    private final MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    private final WalletService walletService;
    private final MediaService mediaService;
    private final AvatarLayerAssembler avatarLayerAssembler;
    private final AvatarPurchaseRepository avatarPurchaseRepository;


    /**
     * 고른 아이템을 <b>한 번에 산다.</b>
     *
     * <p>가격과 결제 재화는 마스터가 갖고 있으므로 <b>클라이언트가 보낸 값을 믿지 않는다.</b>
     * 요청에는 아이템 id 와 멱등 키만 있다.
     *
     * <p><b>재화가 섞여도 된다.</b> 파란 보석 아이템과 주황 보석 아이템을 함께 담으면 재화별로
     * 합계를 내어 각각 차감한다. 상점에 두 재화가 섞여 있으므로, 한 재화로 통일해야만 살 수
     * 있게 하면 사용자가 고른 것을 그대로 살 수 없다.
     *
     * <p><b>전부 되거나 전부 안 되거나.</b> 화면이 "6개 · 4,800" 처럼 묶어서 결제하는데 일부만
     * 성사되면, 사용자는 한 번 결제한 줄 알면서 절반만 반영된 상태를 보게 된다. 한 트랜잭션에
     * 두어 무엇이 막히든 전부 되돌린다.
     *
     * <p><b>모자란 재화는 차감을 시작하기 전에 전부 확인한다.</b> 그냥 차감을 시도하면 먼저 걸린
     * 재화 하나만 알게 되어, 사용자가 그것을 채우고 돌아왔을 때 다른 재화로 또 막힌다.
     *
     * <p>보유 행을 <b>차감보다 먼저</b> 만든다. 따닥으로 두 번 들어오면 유니크 제약이 하나를
     * 떨어뜨리고, 진 쪽은 차감까지 가지 못한다. 리워드 지급이 지급 행을 먼저 만드는 것과 같다.
     *
     * <p><b>입히지 않는다.</b> 같은 자리 아이템을 둘 이상 함께 사면 어느 쪽을 입힐지 정할 수
     * 없다. 서버가 임의로 고르면 사용자가 고르지 않은 착장이 저장되므로, 착용은 착장 저장이
     * 맡는다.
     */
    @Transactional
    public ShopResDTO.PurchaseResult purchase(Long memberId, List<Long> itemIds,
                                              String idempotencyKey) {
        Member member = lockMember(memberId);

        List<Long> requestedIds = itemIds == null ? List.of() : itemIds;
        // 요청 DTO 의 @NotEmpty 가 정상 경로를 막지만 그 방어는 컨트롤러를 거칠 때만 있다.
        // 여기서 걸러 두지 않으면 빈 목록이 아래 지문 계산까지 내려가 서버 오류로 나간다.
        if (requestedIds.isEmpty()) {
            throw new ShopException(ShopErrorCode.EMPTY_CART);
        }

        // 착용과 달리 중복을 조용히 합치지 않는다. 화면이 이미 개수와 금액을 보여준 뒤라,
        // 말없이 하나를 지우면 사용자가 본 금액과 실제 결제액이 어긋난다.
        List<Long> duplicated = duplicatesOf(requestedIds);
        if (!duplicated.isEmpty()) {
            throw new ShopItemRejectedException(ShopErrorCode.DUPLICATE_ITEM, duplicated);
        }

        // 구매를 선점한다. 여기부터가 "이 구매" 라는 실체다.
        //
        // 조회한 뒤 없으면 저장하는데, 이것이 안전한 이유는 위에서 회원 행을 FOR UPDATE 로
        // 잠갔기 때문이다 — 같은 회원의 두 요청은 여기서 줄을 서고, 다른 회원끼리는 유니크가
        // (회원, 키) 라 애초에 부딪히지 않는다. 유니크 제약은 그래도 최종 방어선으로 남는다.
        String fingerprint = CartFingerprint.of(requestedIds);
        Optional<AvatarPurchase> claimed =
                avatarPurchaseRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
        if (claimed.isPresent()) {
            AvatarPurchase previous = claimed.get();
            // 같은 키인데 장바구니가 다르면 진행할 수 없다. 지갑이 같은 키를 "이미 처리한
            // 요청" 으로 보아 차감 없이 예전 결과를 돌려주므로, 그대로 두면 보유 행만 생기고
            // 값이 빠지지 않는다.
            if (!previous.hasSameItems(fingerprint)) {
                throw new ShopException(ShopErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return replayOf(memberId, previous, idempotencyKey);
        }

        // 요청 순서를 지킨다 — 응답의 아이템 순서가 화면의 선택 순서와 같아야 대조하기 쉽다.
        List<AvatarItem> items = itemsInRequestedOrder(requestedIds);
        rejectUnavailable(memberId, items);

        // 재화별 합계. 섞인 요청은 여기서 재화 수만큼의 결제로 갈린다.
        //
        // EnumMap 인 것은 순서 때문이다. 아래에서 재화마다 지갑 행을 잠그는데, 요청마다 순서가
        // 다르면 두 요청이 서로의 다음 행을 기다리는 교착이 생긴다. 선언 순서로 고정된다.
        Map<Currency, Integer> totals = new EnumMap<>(Currency.class);
        for (AvatarItem item : items) {
            totals.merge(item.getCurrency(), item.getPrice(), Integer::sum);
        }
        rejectIfShort(memberId, totals);

        LocalDateTime purchasedAt = LocalDateTime.now(TimeUtil.KST);
        AvatarPurchase purchase = avatarPurchaseRepository.save(AvatarPurchase.builder()
                .member(member)
                .idempotencyKey(idempotencyKey)
                .itemFingerprint(fingerprint)
                .purchasedAt(purchasedAt)
                .build());

        for (AvatarItem item : items) {
            // 유니크 제약이 최종 방어선이다. 위 검사는 흔한 경우를 예외 없이 넘기기 위한
            // 것이고, 동시에 들어오면 둘 다 통과하므로 제약이 하나를 떨군다.
            memberAvatarItemRepository.save(MemberAvatarItem.builder()
                    .member(member)
                    .avatarItem(item)
                    .avatarPurchase(purchase)
                    .currency(item.getCurrency())
                    .paidPrice(item.getPrice())
                    .purchasedAt(purchasedAt)
                    .build());
        }
        memberAvatarItemRepository.flush();

        List<ShopResDTO.Payment> payments = new ArrayList<>();
        for (Map.Entry<Currency, Integer> total : totals.entrySet()) {
            // 멱등 키를 재화별로 나누지 않는다. 원장의 유니크가 (회원, 재화, 키) 라서 같은
            // 키가 재화마다 한 번씩 들어가고, 나누면 오히려 한 구매가 여러 키로 흩어져
            // 되짚기 어려워진다. 교환이 한 사건의 두 재화에 같은 키를 쓰는 것과 같다.
            //
            // 참조는 비운다 — 묶음 구매에는 대표할 아이템이 하나로 정해지지 않는다.
            WalletResult result = walletService.deduct(new WalletCommand(
                    memberId, total.getKey(), WalletTransactionType.PURCHASE,
                    walletIdempotencyKey(idempotencyKey), null, null), total.getValue());
            payments.add(ShopConverter.toPayment(total.getKey(), total.getValue(), result));
        }

        return ShopConverter.toPurchaseResult(items, payments);
    }

    /**
     * 앞서 성사된 구매를 그대로 돌려준다. <b>같은 키·같은 장바구니로 다시 들어왔을 때다.</b>
     *
     * <p>보유 검사를 다시 하지 않는다 — 이미 그 구매로 갖게 된 것이라 {@code ALREADY_OWNED} 가
     * 나가면 정상 재시도가 오류로 보인다. 이 API 가 멱등 키를 받는 이유가 그것이다.
     *
     * <p><b>금액은 마스터가 아니라 구매 시점 스냅샷으로 센다.</b> 그 사이 운영이 가격을 바꿨어도
     * 처음 응답과 같은 값이 나가야 한다.
     *
     * <p>지갑은 다시 부른다. 같은 키라 <b>차감 없이 처음 거래를 돌려주므로</b>({@code applied}
     * 가 {@code false}) 잔액이 두 번 빠지지 않고, 지금 잔액이 응답에 실린다.
     */
    private ShopResDTO.PurchaseResult replayOf(Long memberId, AvatarPurchase purchase,
                                               String idempotencyKey) {
        List<MemberAvatarItem> owned =
                memberAvatarItemRepository.findAllByAvatarPurchaseIdOrderByIdAsc(purchase.getId());

        Map<Currency, Integer> totals = new EnumMap<>(Currency.class);
        for (MemberAvatarItem item : owned) {
            totals.merge(item.getCurrency(), item.getPaidPrice(), Integer::sum);
        }

        List<ShopResDTO.Payment> payments = new ArrayList<>();
        for (Map.Entry<Currency, Integer> total : totals.entrySet()) {
            WalletResult result = walletService.deduct(new WalletCommand(
                    memberId, total.getKey(), WalletTransactionType.PURCHASE,
                    walletIdempotencyKey(idempotencyKey), null, null), total.getValue());
            payments.add(ShopConverter.toPayment(total.getKey(), total.getValue(), result));
        }

        return ShopConverter.toPurchaseResult(
                owned.stream().map(MemberAvatarItem::getAvatarItem).toList(), payments);
    }

    /**
     * 지갑 원장에 남길 키.
     *
     * <p>접두어를 붙여 다른 도메인의 키와 섞이지 않게 한다. 요청 DTO 가 클라이언트 키를 64자로
     * 제한하므로 접두어 16자를 더해도 {@code wallet_transaction.idempotency_key} 의 150자 안에
     * 들어간다 — 그 상한을 넓히려면 여기를 함께 봐야 한다.
     */
    private String walletIdempotencyKey(String idempotencyKey) {
        return "avatar:purchase:" + idempotencyKey;
    }

    /** 두 번 이상 담긴 아이템. 화면에서 지울 대상을 알려주려고 id 를 모은다. */
    private List<Long> duplicatesOf(List<Long> itemIds) {
        Set<Long> seen = new LinkedHashSet<>();
        return itemIds.stream()
                .filter(id -> !seen.add(id))
                .distinct()
                .toList();
    }

    /**
     * 요청한 순서대로 마스터를 붙인다.
     *
     * <p>없는 id 는 <b>어느 것인지 실어</b> 거절한다. 여섯 개 중 무엇이 문제인지 모르면 화면은
     * 전체를 지우는 수밖에 없다.
     */
    private List<AvatarItem> itemsInRequestedOrder(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AvatarItem> found = avatarItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(AvatarItem::getId, Function.identity()));

        List<Long> missing = itemIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ShopItemRejectedException(ShopErrorCode.ITEM_NOT_FOUND, missing);
        }
        return itemIds.stream().map(found::get).toList();
    }

    /**
     * 살 수 없는 아이템을 걸러낸다.
     *
     * <p>판매 여부를 목록 조회와 따로 본다 — 목록에서 감추는 것과 구매를 막는 것은 다르다.
     * 아이템 id 를 아는 클라이언트는 목록을 거치지 않고 구매로 바로 올 수 있다.
     */
    private void rejectUnavailable(Long memberId, List<AvatarItem> items) {
        List<Long> notOnSale = items.stream()
                .filter(item -> !item.isActive())
                .map(AvatarItem::getId)
                .toList();
        if (!notOnSale.isEmpty()) {
            throw new ShopItemRejectedException(ShopErrorCode.ITEM_NOT_ON_SALE, notOnSale);
        }

        // 아바타 아이템은 영구 보유라 두 번 살 이유가 없다. 두 번 결제되면 그대로 손해다.
        List<Long> ids = items.stream().map(AvatarItem::getId).toList();
        List<Long> owned = memberAvatarItemRepository.findOwnedItemIdsIn(memberId, ids);
        if (!owned.isEmpty()) {
            throw new ShopItemRejectedException(ShopErrorCode.ALREADY_OWNED, owned);
        }
    }

    /**
     * 모자란 재화를 <b>전부 모아</b> 한 번에 거절한다.
     *
     * <p>잔액을 잠그고 읽는다. 안 잠그면 확인과 차감 사이에 다른 요청이 잔액을 줄일 수 있고,
     * 그러면 부족분을 실어 보내려던 응답이 결국 지갑의 밋밋한 오류로 나간다.
     */
    private void rejectIfShort(Long memberId, Map<Currency, Integer> totals) {
        List<ShopInsufficientBalanceException.Shortage> shortages = new ArrayList<>();
        for (Map.Entry<Currency, Integer> total : totals.entrySet()) {
            int balance = walletService.lockedBalanceOf(memberId, total.getKey());
            if (balance < total.getValue()) {
                shortages.add(new ShopInsufficientBalanceException.Shortage(
                        total.getKey(), total.getValue(), balance));
            }
        }
        if (!shortages.isEmpty()) {
            throw new ShopInsufficientBalanceException(shortages);
        }
    }

    /**
     * 착장 전체를 저장한다. <b>보낸 것이 곧 전체 착장이고, 목록에 없는 슬롯은 벗는다.</b>
     *
     * <p>지우고 넣는 두 단계가 한 트랜잭션이다. 나뉘면 <b>일부 슬롯만 저장된 착장</b>이 남는다.
     *
     * <p>같은 회원의 동시 저장은 회원 잠금으로 직렬화한다. 안 잠그면 두 요청의 삭제와 삽입이
     * 섞여 <b>어느 쪽에도 없던 조합</b>이 남는다 — 한쪽이 지운 뒤 다른 쪽이 넣는 순서에서는
     * 유니크 제약에도 걸리지 않는다. <b>나중에 온 요청이 이긴다.</b>
     */
    @Transactional
    public ShopResDTO.Avatar equip(Long memberId, List<Long> itemIds) {
        Member member = lockMember(memberId);

        // 같은 아이템을 두 번 보내는 것은 막지 않는다 — 한 번 입히면 되는 요청이라 뜻이
        // 분명하다. 아래 비교가 전부 이 집합을 기준으로 하므로 한 번만 만든다.
        Set<Long> requestedIds = Set.copyOf(itemIds == null ? List.of() : itemIds);

        List<AvatarItem> items = requestedIds.isEmpty()
                ? List.of()
                : avatarItemRepository.findAllById(requestedIds);
        if (items.size() != requestedIds.size()) {
            throw new ShopException(ShopErrorCode.ITEM_NOT_FOUND);
        }

        // 하나라도 미보유면 전체를 거절한다. 미보유 착용은 구매를 건너뛰는 길이 된다.
        if (!items.isEmpty()) {
            Set<Long> owned = Set.copyOf(
                    memberAvatarItemRepository.findOwnedItemIdsIn(memberId, requestedIds));
            if (owned.size() != requestedIds.size()) {
                throw new ShopException(ShopErrorCode.ITEM_NOT_OWNED);
            }
        }

        // 같은 자리에 둘이 오면 어느 쪽을 입힐지 알 수 없다.
        Set<AvatarSlot> seen = EnumSet.noneOf(AvatarSlot.class);
        for (AvatarItem item : items) {
            if (!seen.add(item.getSlot())) {
                throw new ShopException(ShopErrorCode.DUPLICATE_SLOT);
            }
        }

        // 판매 중단(active = 0) 여부는 보지 않는다. 이미 산 것이므로 계속 입을 수 있다 —
        // 판매를 내리는 것과 보유를 뺏는 것은 다르다.
        memberAvatarEquipmentRepository.deleteAllByMemberId(memberId);
        // 지운 행이 아래 삽입과 같은 (member, slot) 을 쓰므로 먼저 반영한다.
        memberAvatarEquipmentRepository.flush();

        List<MemberAvatarEquipment> saved = new ArrayList<>();
        for (AvatarItem item : items) {
            saved.add(MemberAvatarEquipment.builder().member(member).avatarItem(item).build());
        }
        memberAvatarEquipmentRepository.saveAll(saved);

        return ShopConverter.toAvatar(saved,
                avatarLayerAssembler.assembleAsResponse(memberId, saved),
                mediaService::resolveAvatarAssetUrl);
    }

    private Member lockMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
