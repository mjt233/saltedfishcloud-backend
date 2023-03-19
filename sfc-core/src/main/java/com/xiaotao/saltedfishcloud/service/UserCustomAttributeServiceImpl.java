package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.UserCustomAttributeRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.UserCustomAttribute;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserCustomAttributeServiceImpl extends CurdServiceImpl<UserCustomAttribute, UserCustomAttributeRepo> implements UserCustomAttributeService {

    @Override
    public CommonPageInfo<UserCustomAttribute> findByUid(Long uid, PageableRequest pageableRequest) {
        return CommonPageInfo.of(
                repository.findByUid(uid, PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize()))
        );
    }

    @Override
    public List<UserCustomAttribute> findByUid(Long uid) {
        return repository.findByUid(uid);
    }
}
