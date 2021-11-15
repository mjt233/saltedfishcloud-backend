package com.xiaotao.saltedfishcloud.service.share.dao;

import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareDao extends JpaRepository<SharePO, Integer> {
    Page<SharePO> findAllByUidEquals(Integer uid, Pageable page);
}
