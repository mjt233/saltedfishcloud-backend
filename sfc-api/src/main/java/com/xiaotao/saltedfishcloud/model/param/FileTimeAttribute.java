package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import lombok.Data;

import java.util.Date;

@Data
public class FileTimeAttribute {
    private Date createTime;
    private Date modifyTime;
    private Date lastAccessTime;

    /**
     * 将时间属性应用到 FileInfo 对象中（不影响createAt和updateAt）
     * @return 是否有效应用
     */
    public boolean apply(FileInfo fileInfo) {
        boolean isChange = false;
        if (createTime != null) {
            isChange = true;
            fileInfo.setCtime(createTime.getTime());
        }
        if (modifyTime != null) {
            isChange = true;
            fileInfo.setMtime(modifyTime.getTime());
        }
        return isChange;
    }
}
