package com.xiaotao.saltedfishcloud.service.wrap;

import com.xiaotao.saltedfishcloud.model.FileTransferInfo;

public interface WrapService {
    /**
     * 注册一个打包信息
     *
     * @param uid   资源所属用户ID
     * @param files 打包的资源信息
     * @return 打包ID
     */
    String registerWrap(Integer uid, FileTransferInfo files);

    /**
     * 获取打包信息
     *
     * @param wid 打包ID
     * @return 打包信息
     */
    WrapInfo getWrapInfo(String wid);
}
