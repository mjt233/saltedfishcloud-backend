package com.xiaotao.saltedfishcloud.dao.jpa;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProxyInfoRepo extends SfcBaseRepo<ProxyInfo> {
	List<ProxyInfo> findAllByOrderByNameAsc();

	ProxyInfo findByName(String name);

	@Transactional
	long deleteByName(String name);
}
