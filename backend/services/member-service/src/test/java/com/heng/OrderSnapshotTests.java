package com.heng;

import com.heng.biz.*;
import com.heng.config.TradeProperties;
import com.heng.domain.*;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.service.*;
import com.heng.vo.QuoteLineVO;
import com.heng.vo.QuoteVO;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderSnapshotTests {
    @Test
    void orderItemsStoreQuotedPriceAndTotalIncludesPointsDiscount() {
        PricingService pricing = mock(PricingService.class);
        VOrderService orders = mock(VOrderService.class);
        VOrderItemService items = mock(VOrderItemService.class);
        VMemberAddressService addresses = mock(VMemberAddressService.class);
        VCartService cart = mock(VCartService.class);
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<VMemberAddress> addressQuery = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<VCart> cartUpdate = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        when(addresses.lambdaQuery()).thenReturn(addressQuery);
        when(cart.lambdaUpdate()).thenReturn(cartUpdate);
        doReturn(addressQuery).when(addressQuery).eq(any(), any());
        doReturn(cartUpdate).when(cartUpdate).eq(any(), any());
        doReturn(cartUpdate).when(cartUpdate).in(any(), any(Collection.class));
        StockService stock = mock(StockService.class);
        MemberAssetService assets = mock(MemberAssetService.class);
        VGoodsSku sku = new VGoodsSku();
        sku.setSkuId(1L);
        sku.setPriceFen(1000L);
        VGoodsSpu spu = new VGoodsSpu();
        spu.setSpuId(1L);
        spu.setName("Product");
        VMemberAddress address = new VMemberAddress();
        address.setAddressId(1L);
        address.setContactName("Buyer");
        address.setProvince("Province");
        address.setCity("City");
        address.setDistrict("District");
        address.setDetail("Street");
        List<PricingService.Line> request = List.of(new PricingService.Line(1L, 2, null));
        var resolved = List.of(new PricingService.ResolvedLine(sku, spu, null, 2, 1000L, 2000L, 10));
        var quoteLine = new QuoteLineVO(1L, 1L, "Product", "", "", 2, 900L, 1000L, 1800L, 10, null);
        var quote = new QuoteVO(List.of(quoteLine), 2000L, 200L, 0L, 200L,
                100, 100L, 200, 200, 600L, 3900L, 2300L, null, List.of());
        when(pricing.resolveLines(request)).thenReturn(resolved);
        when(pricing.quote(1L, resolved, null, 100)).thenReturn(quote);
        when(addressQuery.one()).thenReturn(address);
        when(orders.save(any())).thenAnswer(call -> { ((VOrder) call.getArgument(0)).setOrderId(1L); return true; });
        AtomicWriteMapper atomic=mock(AtomicWriteMapper.class);
        VMember member=new VMember();member.setStatus(1);when(atomic.lockMember(1L)).thenReturn(member);
        OrderService service = new OrderService(pricing, stock, assets, atomic, orders,
                items, addresses, cart, mock(VSeckillOrderService.class), mock(VPushRecordService.class),
                new TradeProperties());
        VOrder order = service.create(1L, request, 1L, null, 100, null);
        var capture = ArgumentCaptor.forClass(VOrderItem.class);
        verify(items).save(capture.capture());
        assertEquals(900L, capture.getValue().getUnitPriceFen());
        assertEquals(1800L, capture.getValue().getSubtotalFen());
        assertEquals(300L, order.getDiscountAmountFen());
        assertEquals(order.getPayAmountFen(), order.getGoodsAmountFen() - order.getDiscountAmountFen()
                + order.getFreightAmountFen());
    }
}
