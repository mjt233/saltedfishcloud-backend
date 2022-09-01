package com.xiaotao.saltedfishcloud.ext;

import lombok.Builder;
import lombok.Data;

/**
 * Jar依赖包信息
 */
@Data
@Builder
public class JarDependenceInfo {
    /**
     * jar依赖包名称
     */
    private String name;


    /**
     * jar依赖包版本
     */
    private String version;

    @Override
    public String toString() {
        if (version != null) {
            return name + "-" + version;
        } else {
            return name;
        }
    }
}
