package org.ruoyi.domain.bo.legal;

import jakarta.validation.constraints.*;
import java.util.List;

/** Snapshot input never dereferences arbitrary URLs or private chat/case IDs. */
public final class SearchReportInput {
    private SearchReportInput() {}
    public record Create(@NotBlank @Size(max = 200) String title) {}
    /** Null preserves the previous value; an empty background/conclusion clears it. */
    public record Update(@Size(max = 200) String title,
        @Size(max = 50000) String background, @Size(max = 50000) String conclusion) {}
    public record AddItem(
        @NotBlank @Pattern(regexp = "case_full|case_section|law|viewpoint|custom_text|chart") String itemType,
        @Size(max = 200) String refId, @Size(max = 100) String sectionKey,
        @Size(max = 500) String snapshotTitle, @Size(max = 100000) String snapshotContent,
        @Pattern(regexp = "effective|amended|repealed|unknown") String lawStatus,
        @Size(max = 300) String lawName, @Size(max = 100) String articleNo,
        @Size(max = 2000) @Pattern(regexp = "^(https?://[^\\s]+)?$") String sourceUrl,
        @Size(max = 200) String fragmentId) {}
    public record Order(@NotNull @Size(max = 200) List<@NotNull @Positive Long> itemIds) {}
}
