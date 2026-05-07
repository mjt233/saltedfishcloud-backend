package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProxyInfoRepo extends BaseRepo<ProxyInfo> {
	List<ProxyInfo> findAllByOrderByNameAsc();

	ProxyInfo findByName(String name);

	@Transactional
	long deleteByName(String name);
}
