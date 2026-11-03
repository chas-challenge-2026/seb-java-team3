package se.comerit.seb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "payment.approval")
public class ApprovalThresholds {

    private BigDecimal noAttestantThreshold;
    private BigDecimal twoAttestantThreshold;

    // Spring Boot behöver BÅDE getters och setters här - till skillnad
    // från t.ex. User-entityn, som bara hade getters. Anledningen är
    // att Spring fyller i värdena genom att anropa settrarna vid
    // uppstart, med data den läst från application.yml.
    public BigDecimal getNoAttestantThreshold() {
        return noAttestantThreshold;
    }

    public void setNoAttestantThreshold(BigDecimal noAttestantThreshold) {
        this.noAttestantThreshold = noAttestantThreshold;
    }

    public BigDecimal getTwoAttestantThreshold() {
        return twoAttestantThreshold;
    }

    public void setTwoAttestantThreshold(BigDecimal twoAttestantThreshold) {
        this.twoAttestantThreshold = twoAttestantThreshold;
    }
}