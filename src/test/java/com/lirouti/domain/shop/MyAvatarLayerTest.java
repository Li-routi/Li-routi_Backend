package com.lirouti.domain.shop;

import com.lirouti.domain.character.enums.AvatarLayer;
import com.lirouti.domain.character.service.command.CharacterUnlockCommandService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarItem;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.service.command.ShopCommandService;
import com.lirouti.domain.shop.service.query.ShopQueryService;
import com.lirouti.domain.wallet.enums.Currency;
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
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * {@code GET /api/members/me/avatar} 의 레이어 계약.
 *
 * <p>화면은 그림 한 장이 아니라 <b>겹쳐 그린 것</b>이고, 캐릭터는 둥지 사이에 낀다. 그 순서가
 * 문서에만 있고 코드로 지켜지는 곳이 없어 여기에 둔다.
 *
 * <pre>
 * 60  소품        avatar_item (HAND)
 * 50  머리장식     avatar_item (HEAD)
 * 40  윗둥지       캐릭터와 옷을 가린다
 * 30  옷          avatar_item (BODY)
 * 20  캐릭터       avatar_character
 * 10  뒷둥지
 * </pre>
 *
 * <p><b>정렬은 서버가 한다.</b> 앱이 이름을 보고 깊이를 판단하면 레이어가 하나 늘 때마다 앱
 * 배포가 붙는다.
 */
@SpringBootTest
@Transactional
@DisplayName("내 아바타 레이어")
class MyAvatarLayerTest {

    @Autowired private CharacterUnlockCommandService characterUnlockCommandService;
    @Autowired private ShopQueryService shopQueryService;
    @Autowired private ShopCommandService shopCommandService;

    @PersistenceContext private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private AvatarItem hat;    // HEAD
    private AvatarItem shirt;  // BODY
    private AvatarItem ball;   // HAND

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("layer" + n + "@ex.com").nickname("layer" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("layer-sid-" + n).build();
        em.persist(me);

        hat = item(AvatarSlot.HEAD, "리본" + n);
        shirt = item(AvatarSlot.BODY, "앞치마" + n);
        ball = item(AvatarSlot.HAND, "비치볼" + n);
        em.flush();
    }

    @Test
    @DisplayName("둥지·캐릭터·의상이 모두 실리고 z 오름차순으로 나간다")
    void myAvatar_CarriesEveryLayerInOrder() {
        // given — 가입 흐름이 하는 것과 같다. 조건 0개 캐릭터가 그 자리에서 들어온다.
        characterUnlockCommandService.evaluateAndUnlock(me.getId());
        equipAll();

        // when
        ShopResDTO.Avatar avatar = shopQueryService.getMyAvatar(me.getId());

        // then
        assertAll(
                () -> assertThat(avatar.layers())
                        .extracting(layer -> layer.layer())
                        .as("캐릭터가 둥지 사이에 낀다")
                        .containsExactly(
                                AvatarLayer.NEST_BACK,
                                AvatarLayer.CHARACTER,
                                AvatarLayer.BODY,
                                AvatarLayer.NEST_FRONT,
                                AvatarLayer.HEAD,
                                AvatarLayer.HAND),
                () -> assertThat(avatar.layers()).extracting(layer -> layer.z())
                        .as("z 를 함께 내려 프론트가 다시 정렬해도 결과가 같다")
                        .containsExactly(10, 20, 30, 40, 50, 60),
                () -> assertThat(avatar.layers())
                        .as("주소가 비어 있는 레이어가 없다")
                        .allSatisfy(layer -> assertThat(layer.imageUrl()).startsWith("http")),
                () -> assertThat(avatar.equipped())
                        .as("무엇을 입었는지도 그대로 나간다 — layers 와 쓰임이 다르다")
                        .hasSize(3));
    }

    @Test
    @DisplayName("안 입은 자리는 실리지 않는다")
    void myAvatar_OmitsEmptySlots() {
        characterUnlockCommandService.evaluateAndUnlock(me.getId());
        own(hat);
        em.flush();
        em.clear();
        shopCommandService.equip(me.getId(), List.of(hat.getId()));

        ShopResDTO.Avatar avatar = shopQueryService.getMyAvatar(me.getId());

        assertThat(avatar.layers()).extracting(layer -> layer.layer())
                .as("빈 자리를 null 로 채우지 않는다")
                .containsExactly(AvatarLayer.NEST_BACK, AvatarLayer.CHARACTER,
                        AvatarLayer.NEST_FRONT, AvatarLayer.HEAD);
    }

    /**
     * <b>둥지만 뜨면 빈 둥지가, 아이템만 뜨면 허공에 모자가 남는다.</b> 둘 다 "무언가 잘못됐다"
     * 로 보이는 화면이라 차라리 비운다.
     *
     * <p>캐릭터는 가입 시점과 백필로 모두에게 들어가므로 여기 걸리는 사람은 없어야 한다.
     */
    @Test
    @DisplayName("캐릭터가 없으면 입은 것이 있어도 레이어를 통째로 비운다")
    void myAvatar_IsEmptyWithoutCharacter() {
        // given — 해금 판정을 돌리지 않아 캐릭터가 없다.
        equipAll();

        // when
        ShopResDTO.Avatar avatar = shopQueryService.getMyAvatar(me.getId());

        // then
        assertAll(
                () -> assertThat(avatar.layers()).as("그릴 것이 없다").isEmpty(),
                () -> assertThat(avatar.equipped())
                        .as("무엇을 입었는지는 잃지 않는다").hasSize(3));
    }

    // ── 픽스처 ──

    private void equipAll() {
        own(hat);
        own(shirt);
        own(ball);
        em.flush();
        em.clear();
        shopCommandService.equip(me.getId(), List.of(hat.getId(), shirt.getId(), ball.getId()));
        em.flush();
        em.clear();
    }

    private AvatarItem item(AvatarSlot slot, String name) {
        AvatarItem item = AvatarItem.builder()
                .slot(slot).currency(Currency.TOPAZ).price(100)
                .name(name).imageKey("avatar/item/" + name + "-v1.png")
                .sortOrder(1).active(true).build();
        em.persist(item);
        return item;
    }

    private void own(AvatarItem item) {
        em.persist(MemberAvatarItem.builder()
                .member(me).avatarItem(item)
                .currency(item.getCurrency()).paidPrice(item.getPrice())
                .purchasedAt(LocalDateTime.now()).build());
    }
}
