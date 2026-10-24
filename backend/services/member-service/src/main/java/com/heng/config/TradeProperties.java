package com.heng.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "viridac.trade")
public class TradeProperties {
    private long freeShippingThresholdFen = 3900;
    private long shippingFeeFen = 600;
    private int orderTimeoutMinutes = 30;
    private int fenPerPoint = 1;
    private int pointsMaxDeductPercent = 50;
    private int signinPoints = 5;
    private int signinConsecutiveBonusPoints = 5;
    /** 第三方支付回调共享密钥，生产环境必须通过环境变量配置。 */
    private String paymentCallbackSecret = "";
    /** 仅供本地联调的余额充值开关，默认关闭。 */
    private boolean allowDevDeposit = false;
    private boolean sandboxPaymentEnabled = false;
    private boolean demoMode = false;
    private boolean orderTimeoutTaskEnabled = true;

    public boolean isDemoMode() { return demoMode; }
    public void setDemoMode(boolean value) { this.demoMode = value; }
    public boolean isOrderTimeoutTaskEnabled() { return orderTimeoutTaskEnabled; }
    public void setOrderTimeoutTaskEnabled(boolean value) { this.orderTimeoutTaskEnabled = value; }

    public long getFreeShippingThresholdFen() { return freeShippingThresholdFen; }
    public void setFreeShippingThresholdFen(long v) { this.freeShippingThresholdFen = v; }
    public long getShippingFeeFen() { return shippingFeeFen; }
    public void setShippingFeeFen(long v) { this.shippingFeeFen = v; }
    public int getOrderTimeoutMinutes() { return orderTimeoutMinutes; }
    public void setOrderTimeoutMinutes(int v) { this.orderTimeoutMinutes = v; }
    public int getFenPerPoint() { return fenPerPoint; }
    public void setFenPerPoint(int v) { this.fenPerPoint = v; }
    public int getPointsMaxDeductPercent() { return pointsMaxDeductPercent; }
    public void setPointsMaxDeductPercent(int v) { this.pointsMaxDeductPercent = v; }
    public int getSigninPoints() { return signinPoints; }
    public void setSigninPoints(int v) { this.signinPoints = v; }
    public int getSigninConsecutiveBonusPoints() { return signinConsecutiveBonusPoints; }
    public void setSigninConsecutiveBonusPoints(int v) { this.signinConsecutiveBonusPoints = v; }
    public String getPaymentCallbackSecret() { return paymentCallbackSecret; }
    public void setPaymentCallbackSecret(String v) { this.paymentCallbackSecret = v; }
    public boolean isAllowDevDeposit() { return allowDevDeposit; }
    public void setAllowDevDeposit(boolean v) { this.allowDevDeposit = v; }
    public boolean isSandboxPaymentEnabled() { return sandboxPaymentEnabled; }
    public void setSandboxPaymentEnabled(boolean v) { this.sandboxPaymentEnabled = v; }
}
