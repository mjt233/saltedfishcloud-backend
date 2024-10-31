package com.sfc.onlyoffice.service;

import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.model.common.User;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.callback.Status;
import com.onlyoffice.model.documenteditor.config.document.Type;
import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;
import com.onlyoffice.service.documenteditor.config.ConfigService;
import com.sfc.onlyoffice.model.OfficeConfigProperty;
import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

@Service
@Slf4j
public class OfficeService {
    @Autowired
    private ConfigService configService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private SettingsManager settingsManager;

    @Autowired
    private JwtManager jwtManager;

    @Autowired
    private OfficeConfigProperty officeConfigProperty;

    @Autowired
    private DocumentManager documentManager;

    /**
     * 获取请求的资源的文档编辑配置
     * @param resourceRequest   资源请求参数
     * @param requestOriginUrl  原始的请求URL
     * @param isView            是否使用阅读模式打开编辑器
     * @see <a href="https://api.onlyoffice.com/zh/editors/javasdk">ONLYOFFICE API 文档：使用 Java SDK 集成编辑器</a>
     *
     */
    public Config getResourceConfig(ResourceRequest resourceRequest, URL requestOriginUrl, boolean isView) throws UnsupportedProtocolException, IOException {
        if (!StringUtils.hasText(officeConfigProperty.getDocumentServerHost())) {
            throw new JsonException("未配置文档服务器信息，请联系管理员");
        }

        // 获取请求资源的路径唯一标识，确保文件通过文件分享、网盘直接访问的情况下都能指向相同的标识。
        String identity = resourceService.getResourceHandler(resourceRequest.getProtocol()).getPathMappingIdentity(resourceRequest);

        Config config = configService.createConfig(identity, Mode.EDIT, Type.DESKTOP);

        String urlPrefix = StringUtils.hasText(officeConfigProperty.getFileServerHost()) ?
                officeConfigProperty.getFileServerHost() :
                (requestOriginUrl.getProtocol() + "://" + requestOriginUrl.getHost() + ":" + requestOriginUrl.getPort());

        // 构造头像URL和用户信息
        com.xiaotao.saltedfishcloud.model.po.User curUser = SecureUtils.getSpringSecurityUser();
        if (curUser != null) {
            String avatarUrl = Optional
                    .of(curUser)
                    .map(u -> urlPrefix + "/api/user/avatar/" + u.getUser() + "?uid=" + u.getId())
                    .orElse("");
            config.getEditorConfig().setUser(new User(curUser.getId().toString(), curUser.getUsername(), curUser.isAdmin() ? "admin" : "user", avatarUrl));
        }

        // 构造文档下载地址和保存回调配置
        String urlToken = curUser == null ? "" : ("&Token=" + curUser.getToken());
        String filename = resourceRequest.getName();
        config.getEditorConfig().setLang("zh");
        config.getDocument().setFileType(documentManager.getExtension(filename));
        config.getDocument().setTitle(filename);
        config.getDocument().setUrl(urlPrefix + "/api/resource/0/get?" + requestOriginUrl.getQuery() + urlToken);
        config.setDocumentType(documentManager.getDocumentType(filename));
        config.getEditorConfig().setCallbackUrl(urlPrefix + "/api/office/saveCallback?" + requestOriginUrl.getQuery() + urlToken);
        boolean isEdit = !Boolean.TRUE.equals(isView) && Boolean.TRUE.equals(officeConfigProperty.getEnableEdit());
        config.getDocument().getPermissions().setEdit(isEdit);

        if (settingsManager.isSecurityEnabled()) {
            config.setToken(jwtManager.createToken(config));
        }
        return config;
    }

    /**
     * ONLYOFFICE 回调处理程序
     * @param resourceRequest   文档的咸鱼云资源请求参数
     * @param callback          ONLYOFFICE 回调参数
     * @see <a href="https://api.onlyoffice.com/zh/editors/callback">ONLYOFFICE API 文档：回调处理程序</a>
     */
    public void handleCallback(ResourceRequest resourceRequest, Callback callback) throws IOException {
        // 只处理保存请求
        if(callback.getStatus() != Status.SAVE) {
            return;
        }

        if (!Boolean.TRUE.equals(officeConfigProperty.getEnableEdit())) {
            throw new JsonException("服务器未启用编辑功能");
        }

        // 验证请求合法性
        if (Boolean.TRUE.equals(officeConfigProperty.getEnableJwt())) {
            jwtManager.verify(TypeUtils.toString(callback.getToken()));
        }

        // 记录触发回调的操作用户id
        String userId = callback.getUsers().get(0);
        try {
            Optional.ofNullable(resourceRequest.getParams())
                    .orElseGet(() -> {
                        resourceRequest.setParams(new HashMap<>());
                        return resourceRequest.getParams();
                    })
                    .put(ResourceRequest.CREATE_UID, userId);
        } catch (NumberFormatException e) {
            throw new JsonException("不允许匿名用户保存文档");
        }

        // 校验资源能否修改
        ResourceProtocolHandler resourceHandler = resourceService.getResourceHandler(resourceRequest.getProtocol());
        if (!resourceHandler.isWriteable()) {
            throw new JsonException("该资源不可修改");
        }

        // 保存ONLYOFFICE响应的文件
        String downloadUri = callback.getUrl();
        URL url = new URL(downloadUri);
        HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        Path tempDir = PathUtils.getTempPath();
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }
        Path tempFile = tempDir.resolve(System.currentTimeMillis() + "_" + StringUtils.getRandomString(5) + ".office");
        try(InputStream stream = connection.getInputStream();
            OutputStream outputStream = Files.newOutputStream(tempFile)
        ) {
            StreamUtils.copy(stream, outputStream);
            outputStream.close();
            resourceHandler.writeResource(resourceRequest, new PathResource(tempFile));
            log.info("文档保存成功 资源参数: {}", resourceRequest);
        } finally {
            try {
                connection.disconnect();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
