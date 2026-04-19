package com.xiaotao.saltedfishcloud.dao.jpa.projection;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

public interface FileInfoSearchResult {
    FileInfo getFileInfo();

    String getParent();
}
