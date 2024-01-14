package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.ShareInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepo extends JpaRepository<ShareInfo, Long> {
}
