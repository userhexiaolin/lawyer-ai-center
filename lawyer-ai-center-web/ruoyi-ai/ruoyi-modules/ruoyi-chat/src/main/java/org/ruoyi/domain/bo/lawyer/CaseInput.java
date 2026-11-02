package org.ruoyi.domain.bo.lawyer;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record CaseInput(Long id,
    @NotBlank(message="请填写案件标题") @Size(max=200,message="案件标题不能超过200字") String title,
    @Size(max=100) String caseType, @Size(max=200) String clientName, @Size(max=200) String oppositeParty,
    @Pattern(regexp="进行中|已结案|中止",message="请选择有效案件状态") String status,
    @Size(max=200) String court, LocalDate filingDate, LocalDate nextHearingDate, LocalDate limitationDate,
    @Size(max=5000,message="案情摘要不能超过5000字") String description) {}
