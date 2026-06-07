package com.sfc.pxeboot.repo;

import com.sfc.pxeboot.model.po.BootItem;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;

import java.util.List;

/**
 * 启动项数据访问接口
 */
public interface BootItemRepo extends BaseRepo<BootItem> {

    /**
     * 查找所有启用的启动项，按排序顺序排列
     */
    List<BootItem> findByEnabledTrueOrderBySortOrderAsc();

    /**
     * 根据 itemKey 查找启动项
     */
    BootItem findByItemKey(String itemKey);
}
