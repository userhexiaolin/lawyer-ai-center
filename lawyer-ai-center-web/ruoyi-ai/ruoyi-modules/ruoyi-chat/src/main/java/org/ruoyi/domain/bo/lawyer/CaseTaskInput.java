package org.ruoyi.domain.bo.lawyer;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class CaseTaskInput {
    private CaseTaskInput() {}
    public record Create(@NotBlank @Size(max=200) String title,
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime dueAt) {}
    /** Missing fields preserve current values; clearDueAt explicitly clears a deadline. */
    public record Patch(@Size(max=200) String title,
        @Pattern(regexp="todo|doing|done") String status,
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime dueAt,
        Boolean clearDueAt, @Min(0) @Max(1000000) Integer sortOrder) {}
    public record Checklist(@NotBlank @Size(max=1000) String content) {}
    /** checked is optional for legacy toggle callers; clients should send the desired state for retry safety. */
    public record Toggle(Boolean checked) {}
    public record Time(@NotNull @Min(1) @Max(1440) Integer minutes,
        @Size(max=1000) String note, @Size(max=64) @Pattern(regexp="[A-Za-z0-9_-]+") String requestId) {}
}
