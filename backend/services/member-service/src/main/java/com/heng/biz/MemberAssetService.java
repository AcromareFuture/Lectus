package com.heng.biz;

import com.heng.config.BizException;
import com.heng.domain.VMember;
import com.heng.domain.VMemberBalanceLedger;
import com.heng.domain.VMemberPointLedger;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.service.VMemberBalanceLedgerService;
import com.heng.service.VMemberPointLedgerService;
import com.heng.service.VMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分与余额只能通过这里变动，每笔都落流水并用 idempotencyKey 保证同一业务事件只生效一次。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class MemberAssetService {
    private final AtomicWriteMapper atomic;
    @org.springframework.beans.factory.annotation.Autowired private com.heng.config.TradeProperties trade;
    private final com.heng.service.VMemberLevelService levels;
    private final VMemberService memberService;
    private final VMemberPointLedgerService pointLedgerService;
    private final VMemberBalanceLedgerService balanceLedgerService;

    public MemberAssetService(AtomicWriteMapper atomic, VMemberService memberService,
                              VMemberPointLedgerService pointLedgerService,
                              VMemberBalanceLedgerService balanceLedgerService, com.heng.service.VMemberLevelService levels) {
        this.levels=levels;
        this.atomic = atomic;
        this.memberService = memberService;
        this.pointLedgerService = pointLedgerService;
        this.balanceLedgerService = balanceLedgerService;
    }

    public void usePoints(Long memberId, int points, String bizType, Long bizId, String idempotencyKey) {
        if (points <= 0) return;
        lockMember(memberId);
        if (pointLedgerService.lambdaQuery().eq(VMemberPointLedger::getIdempotencyKey, idempotencyKey)
                .last("FOR UPDATE").one() != null) return;
        if (atomic.deductPoints(memberId, points) == 0) {
            throw new BizException("可用积分不足");
        }
        recordPoints(memberId, -points, bizType, bizId, idempotencyKey, null);
    }

    public void addPoints(Long memberId, int points, String bizType, Long bizId, String idempotencyKey, String remark) {
        if (points <= 0) return;
        lockMember(memberId);
        if (pointLedgerService.lambdaQuery().eq(VMemberPointLedger::getIdempotencyKey, idempotencyKey)
                .last("FOR UPDATE").one() != null) return;
        atomic.addPoints(memberId, points);
        recordPoints(memberId, points, bizType, bizId, idempotencyKey, remark);
    }

    public void payWithBalance(Long memberId, long amountFen, Long orderId, String idempotencyKey) {
        if (amountFen <= 0) return;
        lockMember(memberId);
        if (balanceLedgerService.lambdaQuery().eq(VMemberBalanceLedger::getIdempotencyKey, idempotencyKey)
                .last("FOR UPDATE").one() != null) return;
        if (atomic.deductBalance(memberId, amountFen) == 0) {
            throw new BizException("账户余额不足");
        }
        recordBalance(memberId, -amountFen, "PAY", orderId, idempotencyKey);
    }

    public void deposit(Long memberId, long amountFen, String idempotencyKey) {
        if (amountFen <= 0) throw new BizException("充值金额必须大于 0");
        lockMember(memberId);
        if (balanceLedgerService.lambdaQuery().eq(VMemberBalanceLedger::getIdempotencyKey, idempotencyKey)
                .last("FOR UPDATE").one() != null) return;
        atomic.addBalance(memberId, amountFen);
        recordBalance(memberId, amountFen, "DEPOSIT", null, idempotencyKey);
    }

    public void refundBalance(Long memberId,long amount,Long orderId,String key,String source){
        if(amount<=0)throw new BizException("退款金额不合法");current(memberId);
        if(balanceLedgerService.lambdaQuery().eq(VMemberBalanceLedger::getIdempotencyKey,key).last("FOR UPDATE").one()!=null)return;
        atomic.addBalance(memberId,amount);VMemberBalanceLedger ledger=new VMemberBalanceLedger();ledger.setMemberId(memberId);ledger.setChangeAmountFen(amount);ledger.setBalanceAfterFen(current(memberId).getBalanceFen());ledger.setBizType("REFUND");ledger.setBizId(orderId);ledger.setIdempotencyKey(key);ledger.setFundingSource(source);balanceLedgerService.save(ledger);
    }
    public void refundPoints(Long memberId,int points,Long orderId,String key){
        if(points<=0)return;current(memberId);
        if(pointLedgerService.lambdaQuery().eq(VMemberPointLedger::getIdempotencyKey,key).last("FOR UPDATE").one()!=null)return;
        atomic.addPoints(memberId,points);recordPoints(memberId,points,"REFUND_RETURN",orderId,key,"退款返还抵扣积分");
    }
    public void reverseReward(Long memberId,int points,Long orderId,String key){
        if(points<=0)return;current(memberId);
        if(pointLedgerService.lambdaQuery().eq(VMemberPointLedger::getIdempotencyKey,key).last("FOR UPDATE").one()!=null)return;
        // A spent reward becomes a points debt. Refunding money must not fail because points were spent.
        atomic.addPoints(memberId,-points);recordPoints(memberId,-points,"REFUND_REWARD",orderId,key,"退款追回奖励积分，负数将由后续积分抵扣");
        memberService.lambdaUpdate().eq(VMember::getMemberId,memberId).setSql("growth=GREATEST(growth-"+points+",0)").update();
        var member=current(memberId);var level=levels.lambdaQuery().eq(com.heng.domain.VMemberLevel::getStatus,1).le(com.heng.domain.VMemberLevel::getMinGrowth,member.getGrowth()).orderByDesc(com.heng.domain.VMemberLevel::getMinGrowth).last("LIMIT 1").one();if(level!=null)memberService.lambdaUpdate().eq(VMember::getMemberId,memberId).set(VMember::getLevelId,level.getLevelId()).update();
    }

    public void addGrowth(Long memberId, int growth) {
        if (growth > 0) {
            lockMember(memberId);
            atomic.addGrowth(memberId, growth);
            VMember member=current(memberId);
            var level=levels.lambdaQuery().eq(com.heng.domain.VMemberLevel::getStatus,1)
                .le(com.heng.domain.VMemberLevel::getMinGrowth,member.getGrowth())
                .orderByDesc(com.heng.domain.VMemberLevel::getMinGrowth).last("LIMIT 1").one();
            if(level!=null) memberService.lambdaUpdate().eq(VMember::getMemberId,memberId).set(VMember::getLevelId,level.getLevelId()).update();
        }
    }

    private void recordPoints(Long memberId, int change, String bizType, Long bizId,
                              String idempotencyKey, String remark) {
        VMemberPointLedger ledger = new VMemberPointLedger();
        ledger.setMemberId(memberId);
        ledger.setChangePoints(change);
        ledger.setBalanceAfter(current(memberId).getPoints());
        ledger.setBizType(bizType);
        ledger.setBizId(bizId);
        ledger.setIdempotencyKey(idempotencyKey);
        ledger.setRemark(remark);
        pointLedgerService.save(ledger);
    }

    private void recordBalance(Long memberId, long change, String bizType, Long bizId, String idempotencyKey) {
        VMemberBalanceLedger ledger = new VMemberBalanceLedger();
        ledger.setMemberId(memberId);
        ledger.setChangeAmountFen(change);
        ledger.setFundingSource(trade.isDemoMode()?"DEMO":"WALLET");
        ledger.setBalanceAfterFen(current(memberId).getBalanceFen());
        ledger.setBizType(bizType);
        ledger.setBizId(bizId);
        ledger.setIdempotencyKey(idempotencyKey);
        balanceLedgerService.save(ledger);
    }

    private void lockMember(Long memberId) {
        VMember member = atomic.lockMember(memberId);
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())) {
            throw new BizException("会员不存在或已停用");
        }
    }

    private VMember current(Long memberId) {
        VMember member = atomic.lockMember(memberId);
        if (member == null) throw new BizException("会员不存在");
        return member;
    }
}
