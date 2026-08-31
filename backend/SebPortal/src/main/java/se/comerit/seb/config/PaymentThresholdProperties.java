package se.comerit.seb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "app.payments")
public class PaymentThresholdProperties {

    private BigDecimal approvalThreshold = new BigDecimal("50000");
    private BigDecimal doubleApprovalThreshold = new BigDecimal("500000");

    public BigDecimal getApprovalThreshold() {
        return approvalThreshold;
    }

    public void setApprovalThreshold(BigDecimal approvalThreshold) {
        this.approvalThreshold = approvalThreshold;
    }

    public BigDecimal getDoubleApprovalThreshold() {
        return doubleApprovalThreshold;
    }

    public void setDoubleApprovalThreshold(BigDecimal doubleApprovalThreshold) {
        this.doubleApprovalThreshold = doubleApprovalThreshold;
    }
}
