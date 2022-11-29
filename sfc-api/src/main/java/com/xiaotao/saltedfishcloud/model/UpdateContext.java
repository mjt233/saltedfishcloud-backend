package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.Data;

/**
 * 更新上下文
 */
@Data
public class UpdateContext {
    private Version from;
    private Version to;
}
