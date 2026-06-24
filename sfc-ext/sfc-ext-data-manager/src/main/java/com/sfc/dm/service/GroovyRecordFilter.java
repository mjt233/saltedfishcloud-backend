package com.sfc.dm.service;

import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import groovy.lang.Binding;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 基于 Groovy 脚本的记录筛选器。
 * <p>将脚本编译一次，然后对每条记录反复求值。脚本中通过 {@code record} 变量访问当前记录，
 * 最后一行表达式的布尔值决定是否保留该记录。</p>
 * <p>限制条件：</p>
 * <ul>
 *     <li>最大返回结果数：{@value #MAX_RESULT_SIZE} 条，超出后静默截断</li>
 *     <li>最大执行时间：{@value #TIMEOUT_MILLIS} 毫秒，超时后抛出异常</li>
 * </ul>
 */
@Component
public class GroovyRecordFilter {

    /**
     * 筛选结果最大返回条数
     */
    private static final int MAX_RESULT_SIZE = 10000;

    /**
     * 脚本最大执行时间（毫秒）
     */
    private static final long TIMEOUT_MILLIS = 10_000L;

    /**
     * 对记录流执行脚本筛选。
     * <p>结果最多返回 {@value #MAX_RESULT_SIZE} 条，超出静默截断。
     * 脚本执行超过 {@value #TIMEOUT_MILLIS} 毫秒将抛出 {@link JsonException}。</p>
     *
     * @param records 待筛选的记录流
     * @param script  Groovy 脚本代码
     * @return 筛选后的记录 ID 列表（保持输入顺序）
     * @throws JsonException 脚本编译失败或执行超时时抛出
     */
    public List<Long> filter(Stream<InvalidDataRecord> records, String script) {
        try (GroovyScriptExecutor executor = new GroovyScriptExecutor(script)) {
            List<Long> result = new ArrayList<>();
            Iterator<InvalidDataRecord> iterator = records.iterator();

            while (iterator.hasNext()) {
                if (result.size() >= MAX_RESULT_SIZE) {
                    break;
                }

                InvalidDataRecord record = iterator.next();
                Binding binding = new Binding();
                binding.setVariable("record", record);
                String typeCheckResult = record.getTypeCheckResult();
                if (StringUtils.hasText(typeCheckResult)) {
                    try {
                        binding.setVariable("typeCheckResult", MapperHolder.parseJson(typeCheckResult, FileTypeCheckResult.class));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    binding.setVariable("typeCheckResult", null);
                }
                Object value = executor.run(binding, TIMEOUT_MILLIS);
                if (GroovyUtils.isTruthy(value)) {
                    result.add(record.getId());
                }
            }
            return result;
        }
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
