package com.sfc.webshell.service.impl;

import com.sfc.common.service.CrudServiceImpl;
import com.sfc.webshell.model.po.ShellExecuteRecord;
import com.sfc.webshell.repo.ShellExecuteRecordRepo;
import com.sfc.webshell.service.ShellExecuteRecordService;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ShellExecuteRecordServiceImpl
        extends CrudServiceImpl<ShellExecuteRecord, ShellExecuteRecordRepo>
        implements ShellExecuteRecordService {

    @Autowired
    private ClusterService clusterService;

    @Override
    public void addCmdRecord(String workDir, String cmd) {
        ShellExecuteRecord record = new ShellExecuteRecord();
        record.setUid(
                Optional.ofNullable(SecureUtils.getSpringSecurityUser())
                        .map(e -> e.getId().longValue())
                        .orElse((long)User.PUBLIC_USER_ID)
        );
        record.setHost(clusterService.getSelf().getHost());
        record.setCmd(cmd);
        record.setWorkDir(workDir);
        repository.save(record);
    }
}
