package org.ruoyi.domain.entity.lawyer;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lawyer_case")
public class LawyerCase extends TenantEntity {
    @TableId private Long id;
    private Long userId;
    private String title;
    private String caseType;
    private String clientName;
    private String oppositeParty;
    private String status;
    private String court;
    private LocalDate filingDate;
    private LocalDate nextHearingDate;
    private LocalDate limitationDate;
    private String description;
    @TableLogic private Integer deleted;
}
