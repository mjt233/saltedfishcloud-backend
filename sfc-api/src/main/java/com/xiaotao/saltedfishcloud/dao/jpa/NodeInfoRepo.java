package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeInfoRepo extends JpaRepository<NodeInfo, String> {
}
