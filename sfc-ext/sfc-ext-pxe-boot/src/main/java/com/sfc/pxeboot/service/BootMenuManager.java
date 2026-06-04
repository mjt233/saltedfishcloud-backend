package com.sfc.pxeboot.service;

import com.sfc.pxeboot.model.po.BootItem;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 启动菜单管理器
 * 维护活跃启动菜单的内存缓存
 */
@Slf4j
public class BootMenuManager {

    @Autowired
    private BootItemService bootItemService;

    /**
     * -- GETTER --
     *  获取所有活跃启动项
     */
    @Getter
    private volatile List<BootItem> activeItems = Collections.emptyList();

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新活跃启动项列表
     */
    public void refresh() {
        activeItems = bootItemService.findEnabled();
        log.info("[PXE] 启动菜单已刷新，共 {} 个启动项", activeItems.size());
    }

    /**
     * 根据 ID 获取启动项
     */
    public Optional<BootItem> getById(Long id) {
        return activeItems.stream()
            .filter(i -> i.getId().equals(id))
            .findFirst();
    }

    /**
     * 根据 itemKey 获取启动项
     */
    public Optional<BootItem> getByItemKey(String itemKey) {
        return activeItems.stream()
            .filter(i -> itemKey != null && itemKey.equals(i.getItemKey()))
            .findFirst();
    }
}
