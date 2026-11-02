package org.ruoyi.domain.entity.lawyer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.ruoyi.common.tenant.core.TenantEntity;
import org.ruoyi.service.lawyer.BankStatementParser;

@Data @EqualsAndHashCode(callSuper=true) @TableName("lawyer_case_bank_row")
public class CaseBankRow extends TenantEntity {
    @TableId
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long userId;
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long fileVersionId;
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long pageId;
    private Integer pageIndex;
    private Integer tableRow;
    private Integer fragmentStart;
    private Integer sourceVariant;
    private Integer fragmentEnd;
    @JsonIgnore
    private String fragmentText;
    private String pageHash;
    private String sourceHash;
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    private Long lastRunId;
    private String suggestionJson;
    private String tradeTimeRaw;
    private String tradeAt;
    private String amountRaw;
    @JsonSerialize(using=ToStringSerializer.class)
    private java.math.BigDecimal amount;
    private String direction;
    private String currency;
    private String balanceRaw;
    @JsonSerialize(using=ToStringSerializer.class)
    private java.math.BigDecimal balance;
    @JsonIgnore
    private String accountRaw;
    @JsonIgnore
    private String counterAccountRaw;
    @JsonIgnore
    private String counterpartyRaw;
    private String transactionRef;
    private String summary;
    private String reasonsJson;
    private String anomalyJson;
    private String reviewStatus;
    private Boolean included;
    private Boolean edited;
    private Integer version;
    public String getAccount(){return BankStatementParser.mask(accountRaw);}
    public String getCounterAccount(){return BankStatementParser.mask(counterAccountRaw);}
    public String getCounterparty(){return BankStatementParser.mask(counterpartyRaw);}
}
