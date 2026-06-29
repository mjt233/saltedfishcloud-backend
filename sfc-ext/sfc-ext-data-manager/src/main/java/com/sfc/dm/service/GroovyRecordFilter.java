package com.sfc.dm.service;

import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 基于 Groovy 脚本的记录筛选器。
 * <p>将脚本编译一次，然后对每条记录反复求值。脚本中可通过以下变量访问数据：</p>
 * <ul>
 *     <li>{@code record} — 当前记录（{@link InvalidDataRecord}）</li>
 *     <li>{@code typeCheckResult} — 文件类型检查结果（{@link FileTypeCheckResult}，可能为 null）</li>
 *     <li>{@code context} — 跨迭代共享的上下文 Map，可在多次脚本执行间存取数据</li>
 * </ul>
 * <p>{@code com.xiaotao.saltedfishcloud.utils} 包下的所有工具类已默认导入，可直接使用类名访问。</p>
 * <p>最后一行表达式的布尔值决定是否保留该记录。</p>
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
     * 脚本执行超过 {@value #TIMEOUT_MILLIS} 毫秒将抛出 JsonException。</p>
     *
     * @param records 待筛选的记录流
     * @param script  Groovy 脚本代码
     * @return 筛选后的记录 ID 列表（保持输入顺序）
     * @throws JsonException 脚本编译失败或执行超时时抛出
     */
    public List<Long> filter(Stream<InvalidDataRecord> records, String script) {
        try (GroovyScriptExecutor executor = InvalidDataGroovyScriptHelper.createExecutor(script)) {
            List<Long> result = new ArrayList<>();
            Iterator<InvalidDataRecord> iterator = records.iterator();
            Map<String, Object> context = new HashMap<>();

            while (iterator.hasNext()) {
                if (result.size() >= MAX_RESULT_SIZE) {
                    break;
                }

                InvalidDataRecord record = iterator.next();
                Object value = executor.run(InvalidDataGroovyScriptHelper.createBinding(record, context), TIMEOUT_MILLIS);
                if (GroovyUtils.isTruthy(value)) {
                    result.add(record.getId());
                }
            }
            return result;
        }
    }

    /**
     * 对记录流执行脚本筛选，返回筛选后的记录流。
     * <p>结果最多返回 {@value #MAX_RESULT_SIZE} 条，超出静默截断。
     * 脚本执行超过 {@value #TIMEOUT_MILLIS} 毫秒将抛出 JsonException。</p>
     * <p>与 {@link #filter(Stream, String)} 语义一致，区别在于返回值为记录对象流而非 ID 列表，
     * 用于需要直接消费记录对象的场景（如批量认领）。</p>
     *
     * @param records 待筛选的记录流
     * @param script  Groovy 脚本代码
     * @return 筛选后的记录流（保持输入顺序）
     * @throws JsonException 脚本编译失败或执行超时时抛出
     */
    public Stream<InvalidDataRecord> filterStream(Stream<InvalidDataRecord> records, String script) {
        try (GroovyScriptExecutor executor = InvalidDataGroovyScriptHelper.createExecutor(script)) {
            List<InvalidDataRecord> result = new ArrayList<>();
            Iterator<InvalidDataRecord> iterator = records.iterator();
            Map<String, Object> context = new HashMap<>();

            while (iterator.hasNext()) {
                if (result.size() >= MAX_RESULT_SIZE) {
                    break;
                }

                InvalidDataRecord record = iterator.next();
                assert executor != null;
                Object value = executor.run(InvalidDataGroovyScriptHelper.createBinding(record, context), TIMEOUT_MILLIS);
                if (GroovyUtils.isTruthy(value)) {
                    result.add(record);
                }
            }
            return result.stream();
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
