package com.sfc.task.prog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ProgressRecordVO {
    private String taskId;

    private ProgressRecord record;
}
