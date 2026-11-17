package com.sfc.download.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SeedTaskBatchUtils} 单元测试：分批语义必须与宿主 {@code CollectionUtils.partition} 等价。
 */
class SeedTaskBatchUtilsTest {

    @Test
    @DisplayName("空集合 / null 返回空批次列表")
    void partitionEmpty() {
        assertTrue(SeedTaskBatchUtils.partition(null, 10).isEmpty());
        assertTrue(SeedTaskBatchUtils.partition(List.of(), 10).isEmpty());
    }

    @Test
    @DisplayName("元素数量不超过批次大小时只返回一个批次")
    void partitionSingleBatch() {
        List<List<Integer>> batches = SeedTaskBatchUtils.partition(List.of(1, 2, 3), 5);
        assertEquals(1, batches.size());
        assertEquals(List.of(1, 2, 3), batches.getFirst());
    }

    @Test
    @DisplayName("按固定大小拆分，最后一批可能不足")
    void partitionMultipleBatches() {
        List<List<Integer>> batches = SeedTaskBatchUtils.partition(List.of(1, 2, 3, 4, 5), 2);
        assertEquals(3, batches.size());
        assertEquals(List.of(1, 2), batches.getFirst());
        assertEquals(List.of(3, 4), batches.get(1));
        assertEquals(List.of(5), batches.get(2));
    }

    @Test
    @DisplayName("拆分结果与入参集合解耦（修改原集合不影响批次）")
    void partitionIsIndependentOfSource() {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4));
        List<List<Integer>> batches = SeedTaskBatchUtils.partition(source, 2);
        source.clear();
        assertEquals(List.of(1, 2), batches.getFirst());
        assertEquals(List.of(3, 4), batches.get(1));
    }

    @Test
    @DisplayName("非 List 集合同样支持，且批次内顺序与集合迭代顺序一致")
    void partitionFromNonListCollection() {
        List<List<Integer>> batches = SeedTaskBatchUtils.partition(
                new LinkedHashSet<>(List.of(9, 8, 7)), 2);
        assertEquals(2, batches.size());
        assertEquals(List.of(9, 8), batches.getFirst());
        assertEquals(List.of(7), batches.get(1));
    }

    @Test
    @DisplayName("非法批次大小抛出 IllegalArgumentException")
    void partitionRejectsInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> SeedTaskBatchUtils.partition(List.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> SeedTaskBatchUtils.partition(List.of(1), -1));
    }
}
