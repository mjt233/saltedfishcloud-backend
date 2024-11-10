package com.xiaotao.saltedfishcloud.service.wrap;

import com.xiaotao.saltedfishcloud.model.FileTransferInfo;
import com.xiaotao.saltedfishcloud.model.param.WrapParam;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

public interface WrapService {
    /**
     * 注册一个打包信息
     *
     * @param uid   资源所属用户ID
     * @param files 打包的资源信息
     * @return 打包ID
     */
    String registerWrap(Long uid, FileTransferInfo files);

    /**
     * 获取打包信息
     *
     * @param wid 打包ID
     * @return 打包信息
     */
    WrapInfo getWrapInfo(String wid);

    /**
     * 根据复杂打包参数创建打包
     * @param param 参数
     * @return      打包id
     */
    String registerWrap(WrapParam param);

    /**
     * 生成打包的内容并输出到输出流
     * @param wid           打包id
     * @param outputStream  输出流
     */
    void writeWrapToStream(String wid, OutputStream outputStream) throws IOException;

    /**
     * 直接生成打包的内容并输出到servlet响应中
     * @param wid           打包id
     * @param alias         响应文件别名
     * @param response      响应对象
     */
    void writeWrapToServlet(String wid, String alias, HttpServletResponse response) throws IOException;
}
