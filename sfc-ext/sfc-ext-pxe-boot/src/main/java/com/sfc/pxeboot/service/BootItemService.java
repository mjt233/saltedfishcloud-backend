package com.sfc.pxeboot.service;

import com.sfc.pxeboot.model.dto.BootItemDTO;
import com.sfc.pxeboot.model.po.BootItem;

import java.util.List;

/**
 * 启动项服务接口
 */
public interface BootItemService {

    /**
     * 获取所有启动项
     */
    List<BootItem> findAll();

    /**
     * 获取所有启用的启动项（按排序顺序）
     */
    List<BootItem> findEnabled();

    /**
     * 根据 ID 获取启动项
     */
    BootItem findById(Long id);

    /**
     * 创建启动项
     */
    BootItem create(BootItemDTO dto, Long uid);

    /**
     * 更新启动项
     */
    BootItem update(Long id, BootItemDTO dto);

    /**
     * 删除启动项
     */
    void delete(Long id);

    /**
     * 启用启动项
     */
    void enable(Long id);

    /**
     * 禁用启动项
     */
    void disable(Long id);

    /**
     * 更新排序顺序
     */
    void reorder(List<Long> orderedIds);
}
