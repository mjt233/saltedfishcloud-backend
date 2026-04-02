package com.xiaotao.saltedfishcloud.model.progress;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Data
public abstract class CopyProgressEvent {
    /**
     * 事件等级
     */
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
