package com.xiaotao.saltedfishcloud.download.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.task.AsyncTask;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.helper.CustomLogger;
import com.xiaotao.saltedfishcloud.model.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.download.IgnoreSSLHttpRequestFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Setter;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.Proxy;
import java.nio.file.NoSuchFileException;

public class DownloadAsyncTask implements AsyncTask {
    private final String originParams;

    private final DownloadTaskParams params;

    private CustomLogger logger;

    @Setter
    private ProxyDao proxyDao;

    @Setter
    private NodeService nodeService;

    private RestTemplate restTemplate;

    private Proxy sysProxy;

    private final ProgressRecord progressRecord = new ProgressRecord()
            .setTotal(-1)
            .setLoaded(0);

    public DownloadAsyncTask(String originParams) {
        try {
            this.originParams = originParams;
            this.params = MapperHolder.parseJson(originParams, DownloadTaskParams.class);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("参数解析失败", e);
        }

    }

    @Override
    public void execute(OutputStream logOutputStream) {
        logger = new CustomLogger(logOutputStream);

        validPath();
        initProxy();
        initRestTemplate();

    }

    private void initRestTemplate() {
        IgnoreSSLHttpRequestFactory factory = new IgnoreSSLHttpRequestFactory();
        factory.setConnectTimeout(params.connectTimeout);
        factory.setReadTimeout(params.readTimeout);

        if (sysProxy != null) {
            factory.setProxy(sysProxy);
        }
        restTemplate = new RestTemplate(factory);
    }

    private void initProxy() {
        if (params.proxy != null && params.proxy.length() != 0) {
            ProxyInfo proxy = proxyDao.getProxyByName(params.proxy);
            if (proxy == null) {
                throw new IllegalArgumentException("无效的代理：" + params.proxy);
            }
            sysProxy = proxy.toProxy();
            logger.debug("使用代理创建下载任务: " + proxy);
        }
    }

    private void validPath() {

        // 校验参数合法性
        try {
            logger.info("检查路径" + params.uid + " - " + params.savePath);
            nodeService.getPathNodeByPath(params.uid, params.savePath);
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("保存路径不存在" + params.savePath);
        }
    }

    @Override
    public void interrupt() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public String getParams() {
        return originParams;
    }

    @Override
    public ProgressRecord getProgress() {
        return progressRecord;
    }
}
