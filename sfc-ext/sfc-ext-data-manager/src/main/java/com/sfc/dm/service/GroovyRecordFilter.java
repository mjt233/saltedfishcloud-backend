package com.sfc.dm.service;

import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 基于 Groovy 脚本的记录筛选器。
 * <p>将脚本编译一次，然后对每条记录反复求值。脚本中通过 {@code record} 变量访问当前记录，
 * 最后一行表达式的布尔值决定是否保留该记录。</p>
 */
@Component
public class GroovyRecordFilter {

    /**
     * 对记录流执行脚本筛选。
     *
     * @param records 待筛选的记录流
     * @param script  Groovy 脚本代码
     * @return 筛选后的记录 ID 列表（保持输入顺序）
     * @throws JsonException 脚本编译或执行出错时抛出
     */
    public List<Long> filter(Stream<InvalidDataRecord> records, String script) {
        Script compiled;
        try {
            compiled = new GroovyShell().parse(script);
        } catch (CompilationFailedException e) {
            throw new JsonException("脚本编译失败: " + e.getMessage());
        }

        List<Long> result = new ArrayList<>();
        records.forEach(record -> {
            try {
                Binding binding = new Binding();
                binding.setVariable("record", record);
                compiled.setBinding(binding);
                Object value = compiled.run();
                if (GroovyUtils.isTruthy(value)) {
                    result.add(record.getId());
                }
            } catch (Exception e) {
                throw new JsonException("脚本执行异常[recordId=" + record.getId() + "]: " + e.getMessage());
            }
        });

        return result;
    }

    /**
     * Groovy truthiness 判断工具
     */
    private static final class GroovyUtils {
        static boolean isTruthy(Object value) {
            if (value == null) {
                return false;
            }
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return true; // 非 null 非 boolean 按 Groovy truthy 处理
        }
    }
}
