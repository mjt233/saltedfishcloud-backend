package com.sfc.dm.service;

import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import groovy.lang.Binding;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Groovy 脚本执行工具类，封装失效数据模块中通用的脚本执行模式。
 */
public final class InvalidDataGroovyScriptHelper {

    /**
     * 创建 Groovy 脚本执行器，自动添加 com.xiaotao.saltedfishcloud.utils 星号导入。
     *
     * @param script 脚本代码，为 null 或空时返回 null
     * @return 脚本执行器，脚本为空时返回 null
     */
    public static GroovyScriptExecutor createExecutor(String script) {
        if (!StringUtils.hasText(script)) {
            return null;
        }
        return new GroovyScriptExecutor(script, config -> {
            ImportCustomizer ic = new ImportCustomizer();
            ic.addStarImports("com.xiaotao.saltedfishcloud.utils");
            config.addCompilationCustomizers(ic);
        });
    }

    /**
     * 为失效数据记录创建 Binding，包含 record、context（新 Map）、typeCheckResult 变量。
     *
     * @param record 当前失效数据记录
     * @return Binding 实例
     */
    public static Binding createBinding(InvalidDataRecord record) {
        return createBinding(record, new HashMap<>());
    }

    /**
     * 为失效数据记录创建 Binding，包含 record、context、typeCheckResult 变量。
     *
     * @param record  当前失效数据记录
     * @param context 跨迭代共享的上下文 Map
     * @return Binding 实例
     */
    public static Binding createBinding(InvalidDataRecord record, Map<String, Object> context) {
        Binding binding = new Binding();
        binding.setVariable("record", record);
        binding.setVariable("context", context);
        String typeCheckResult = record.getTypeCheckResult();
        if (StringUtils.hasText(typeCheckResult)) {
            try {
                binding.setVariable("typeCheckResult",
                        MapperHolder.parseJson(typeCheckResult, FileTypeCheckResult.class));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            binding.setVariable("typeCheckResult", null);
        }
        return binding;
    }
}
