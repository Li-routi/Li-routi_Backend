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
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.exception.ShopException;
import com.lirouti.domain.shop.exception.code.error.ShopErrorCode;
import com.lirouti.domain.shop.repository.AvatarItemRepository;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import com.lirouti.global.util.TimeUtil;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 저장된 S3 key 를 볼 수 있는 주소로 바꾼다.
     *
     * <p>아바타 자산은 공개 prefix 라 서명 없이 조립만 한다 — S3 를 부르지 않으므로 목록에서
     * 행마다 불러도 비용이 얹히지 않는다.
     */
    private String toViewUrl(String imageKey) {
        return mediaService.resolveViewUrl(imageKey, MediaPurpose.AVATAR_ASSET);
    }


    /**
     * 아이템을 사고 <b>그 자리에 바로 입힌다.</b>
     *
     * <p>요청은 아이템 id 하나다 — 가격과 결제 재화는 마스터가 갖고 있으므로 <b>클라이언트가
     * 보낸 값을 믿지 않는다.</b>
     *
     * <p>보유 행을 <b>차감보다 먼저</b> 만든다. 따닥으로 두 번 들어오면 유니크 제약이 하나를
     * 떨어뜨리고, 진 쪽은 차감까지 가지 못한다. 리워드 지급이 지급 행을 먼저 만드는 것과 같다.
     *
     * <p>착용은 <b>그 아이템의 슬롯만</b> 건드린다 — 손에 든 것을 샀다고 모자가 벗겨지면 안 된다.
     */
    @Transactional
    public ShopResDTO.Avatar purchase(Long memberId, Long itemId) {
        Member member = lockMember(memberId);

        AvatarItem item = avatarItemRepository.findById(itemId)
                .orElseThrow(() -> new ShopException(ShopErrorCode.ITEM_NOT_FOUND));

        // 목록에서 감추는 것과 구매를 막는 것은 다르다. 아이템 id 를 아는 클라이언트는
        // 목록을 거치지 않고 여기로 바로 올 수 있다.
        if (!item.isActive()) {
            throw new ShopException(ShopErrorCode.ITEM_NOT_ON_SALE);
        }
        if (memberAvatarItemRepository.existsByMemberIdAndAvatarItemId(memberId, itemId)) {
            throw new ShopException(ShopErrorCode.ALREADY_OWNED);
        }

        // 유니크 제약이 최종 방어선이다. 위 조회는 흔한 경우를 예외 없이 넘기기 위한 것이고,
        // 동시에 들어오면 둘 다 통과하므로 제약이 하나를 떨군다.
        memberAvatarItemRepository.saveAndFlush(MemberAvatarItem.builder()
                .member(member)
                .avatarItem(item)
                .currency(item.getCurrency())
                .paidPrice(item.getPrice())
                .purchasedAt(LocalDateTime.now(TimeUtil.KST))
                .build());

        // 잔액이 모자라면 여기서 예외가 나가고 보유 행도 함께 롤백된다.
        walletService.deduct(new WalletCommand(
                memberId, item.getCurrency(), WalletTransactionType.PURCHASE,
                "avatar:purchase:" + itemId, "AVATAR_ITEM", itemId), item.getPrice());

        equipOne(member, item);
        return currentAvatar(memberId);
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

        return ShopConverter.toAvatar(saved, this::toViewUrl);
    }

    /** 그 아이템의 슬롯만 교체한다. 다른 자리는 그대로다. */
    private void equipOne(Member member, AvatarItem item) {
        memberAvatarEquipmentRepository
                .findByMemberIdAndSlot(member.getId(), item.getSlot())
                .ifPresent(memberAvatarEquipmentRepository::delete);
        memberAvatarEquipmentRepository.flush();
        memberAvatarEquipmentRepository.save(
                MemberAvatarEquipment.builder().member(member).avatarItem(item).build());
    }

    private ShopResDTO.Avatar currentAvatar(Long memberId) {
        return ShopConverter.toAvatar(
                memberAvatarEquipmentRepository.findAllByMemberId(memberId), this::toViewUrl);
    }

    private Member lockMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
