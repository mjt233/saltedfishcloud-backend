package com.sfc.pxeboot.service;

import com.sfc.pxeboot.model.dto.BootItemDTO;
import com.sfc.pxeboot.model.po.BootItem;
import com.sfc.pxeboot.repo.BootItemRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 启动项服务实现
 */
@Slf4j
public class BootItemServiceImpl implements BootItemService {

    @Autowired
    private BootItemRepo bootItemRepo;

    @Override
    public List<BootItem> findAll() {
        return bootItemRepo.findAll();
    }

    @Override
    public List<BootItem> findEnabled() {
        return bootItemRepo.findByEnabledTrueOrderBySortOrderAsc();
    }

    @Override
    public BootItem findById(Long id) {
        return bootItemRepo.findById(id)
            .orElseThrow(() -> new JsonException("启动项不存在: " + id));
    }

    @Override
    public BootItem create(BootItemDTO dto, Long uid) {
        BootItem item = new BootItem();
        BeanUtils.copyProperties(dto, item);
        item.setUid(uid);
        if (item.getEnabled() == null) {
            item.setEnabled(true);
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        return bootItemRepo.save(item);
    }

    @Override
    public BootItem update(Long id, BootItemDTO dto) {
        BootItem item = findById(id);
        BeanUtils.copyProperties(dto, item, "id", "uid", "createAt", "updateAt");
        return bootItemRepo.save(item);
    }

    @Override
    public void delete(Long id) {
        if (!bootItemRepo.existsById(id)) {
            throw new JsonException("启动项不存在: " + id);
        }
        bootItemRepo.deleteById(id);
    }

    @Override
    public void enable(Long id) {
        BootItem item = findById(id);
        item.setEnabled(true);
        bootItemRepo.save(item);
    }

    @Override
    public void disable(Long id) {
        BootItem item = findById(id);
        item.setEnabled(false);
        bootItemRepo.save(item);
    }

    @Override
    public void reorder(List<Long> orderedIds) {
        List<BootItem> items = bootItemRepo.findByIds(orderedIds);
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            final int sortOrder = i;
            items.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .ifPresent(item -> {
                    item.setSortOrder(sortOrder);
                    bootItemRepo.save(item);
                });
        }
    }
}
