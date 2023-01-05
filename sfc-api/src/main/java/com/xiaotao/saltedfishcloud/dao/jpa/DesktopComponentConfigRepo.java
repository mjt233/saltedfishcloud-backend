package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.DesktopComponentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DesktopComponentConfigRepo extends JpaRepository<DesktopComponentConfig, Long> {
    List<DesktopComponentConfig> findByUidOrderByShowOrder(Long uid);

    DesktopComponentConfig findByName(String name);

    @Modifying
    @Transactional
    void deleteById(Long id);
}
