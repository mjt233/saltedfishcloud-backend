package com.sfc.download.seed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 做种模块的本地分批工具。
 *
 * <p><b>为什么不直接用宿主工具：</b>项目规范推荐使用
 * {@code com.xiaotao.saltedfishcloud.utils.CollectionUtils#partition}，但该类位于 {@code sfc-api}
 * （宿主侧），而下载微服务遵循「零宿主依赖」，类路径上不存在它。因此在本模块内提供行为等价的实现，
 * 供批量查询、批量状态回写等场景按固定大小分批，避免一次 SQL 传入过多参数。若后续共享库提供统一的分批工具，
 * 应改用共享实现并删除本类。</p>
 *
 * <p>语义与宿主实现保持一致：按固定大小切分，不修改入参集合；集合为空时返回空列表；每个批次是非空子列表。
 * 本类无状态、线程安全。</p>
 */
public final class SeedTaskBatchUtils {

    /**
     * 单批处理的默认大小。
     *
     * <p>取值与下载服务其它批量查询（向 core 查询异步任务状态）保持一致，避免单条 SQL 的 IN 参数过多。</p>
     */
    public static final int DEFAULT_BATCH_SIZE = 500;

    /**
     * 工具类禁止实例化。
     */
    private SeedTaskBatchUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 把集合按指定大小拆分为多个批次。
     *
     * @param collection 待拆分的集合，允许为 {@code null}（返回空列表）
     * @param size       每个批次的最大元素数量，必须大于 0
     * @param <T>        元素类型
     * @return 拆分后的批次列表；入参为空时返回空列表（而非包含空批次的列表）
     * @throws IllegalArgumentException {@code size} 不大于 0 时抛出
     */
    public static <T> List<List<T>> partition(Collection<T> collection, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("分批大小必须大于0: " + size);
        }
        if (collection == null || collection.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<T>> batches = new ArrayList<>((collection.size() + size - 1) / size);
        List<T> current = new ArrayList<>(Math.min(size, collection.size()));
        for (T item : collection) {
            if (current.size() >= size) {
                batches.add(current);
                current = new ArrayList<>(Math.min(size, collection.size()));
            }
            current.add(item);
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }
}
