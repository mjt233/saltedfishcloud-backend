package com.xiaotao.saltedfishcloud.model.progress;

import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.Optional;

@Builder
@Data
public class CopyProgressEvent {
    /**
     * 事件等级
     */
    @Builder.Default
    private CopyProgressEventLevel level = CopyProgressEventLevel.INFO;

    /**
     * 事件名称
     */
    private String name;

    /**
     * 事件消息
     */
    private String message;

    /**
     * 在执行哪个文件的传输时触发的事件
     */
    @Nullable
    private FileTransferItem transferItem;

    @Override
    public String toString() {
        return "[" + level + "][" + name + "]" + message + " from: " + Optional.ofNullable(transferItem).map(FileTransferItem::getFrom).orElse("");
    }
}
