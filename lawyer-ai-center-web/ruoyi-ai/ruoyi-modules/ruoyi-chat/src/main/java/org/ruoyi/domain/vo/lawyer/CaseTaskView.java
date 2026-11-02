package org.ruoyi.domain.vo.lawyer;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public final class CaseTaskView {
    private CaseTaskView() {}
    public record Checklist(@JsonFormat(shape=JsonFormat.Shape.STRING) Long id,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Long taskId, String content, boolean checked,
        LocalDateTime checkedAt, LocalDateTime createdAt) {}
    public record Task(@JsonFormat(shape=JsonFormat.Shape.STRING) Long id,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Long caseId,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Long userId,
        String title, String status, LocalDateTime dueAt, int sortOrder, long workMinutes,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<Checklist> checklist) {}
    public record Summary(List<Task> tasks, int doneTasks, int totalTasks, int checkedItems, int totalItems, long workMinutes) {}
}
