package com.xiaotao.saltedfishcloud.download.model;

import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import org.hibernate.validator.constraints.URL;
import org.springframework.http.HttpMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * 离线下载任务参数
 */
public class DownloadTaskParams {
    /**
     * 要下载的文件URL
     */
    @URL
    @NotBlank
    public String url;

    /**
     * 下载任务表的id
     */
    public String downloadId;

    /**
     * 用户id
     */
    @UID
    public long uid;

    /**
     * 额外的headers
     */
    public Map<String, String> headers;

    /**
     * 请求方法
     * @see org.springframework.http.HttpHeaders
     */
    public String method = HttpMethod.GET.name();

    /**
     * 使用的代理（在代理服务中配置的代理名称）
     */
    public String proxy;

    /**
     * 连接超时设定（默认10秒）
     */
    public int connectTimeout = 10000;

    /**
     * 读数据超时设定（默认一分钟）
     */
    public int readTimeout = 60000;

    /**
     * 保存到网盘的所在路径
     */
    @NotEmpty
    public String savePath;

    @Override
    public String toString() {
        return "DownloadTaskParams{" +
                "url='" + url + '\'' +
                ", headers=" + headers +
                ", method=" + method +
                ", proxy='" + proxy + '\'' +
                ", savePath='" + savePath + '\'' +
                '}';
    }
}
