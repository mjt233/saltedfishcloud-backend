package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.UserCustomAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCustomAttributeRepo extends JpaRepository<UserCustomAttribute, Long> {
    Page<UserCustomAttribute> findByUid(Long uid, Pageable pageable);

    List<UserCustomAttribute> findByUid(Long uid);
}
