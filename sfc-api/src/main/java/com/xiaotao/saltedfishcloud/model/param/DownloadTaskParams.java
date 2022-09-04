package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import org.hibernate.validator.constraints.URL;
import org.springframework.http.HttpMethod;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.Map;

public class DownloadTaskParams {
    @URL
    @NotBlank
    public String url;

    @UID
    public int uid;
    public Map<String, String> headers;
    public HttpMethod method = HttpMethod.GET;
    public String proxy;

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
