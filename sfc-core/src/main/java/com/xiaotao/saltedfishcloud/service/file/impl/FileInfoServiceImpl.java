package com.xiaotao.saltedfishcloud.service.file.impl;

import com.sfc.common.service.CrudServiceImpl;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileInfoService;
import org.springframework.stereotype.Service;

@Service
public class FileInfoServiceImpl extends CrudServiceImpl<FileInfo, FileInfoRepo> implements FileInfoService {
}
