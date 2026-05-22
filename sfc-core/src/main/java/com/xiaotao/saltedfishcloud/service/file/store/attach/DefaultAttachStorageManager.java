package com.xiaotao.saltedfishcloud.service.file.store.attach;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 默认的附属存储服务管理器实现。
 * <p>
 * 所有附属存储文件都会被限制在 {@code sys.store.root/attach/<storageDomainId>} 目录下，
 * 即使调用方在路径中传入 {@code .} 或 {@code ..} 也不允许越过该目录。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultAttachStorageManager implements AttachStorageManager {
    private static final String ATTACH_ROOT_DIR = "attach";
    private static final Pattern STORAGE_DOMAIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final StoreServiceFactory storeServiceFactory;
    private final SysProperties sysProperties;

    private final Map<String, AttachStorageDomainDefinition> definitionMap = new ConcurrentHashMap<>();
    private final Map<String, AttachStorage> storageMap = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public AttachStorage getStorage(String storageDomainId) {
        String normalizedStorageDomainId = normalizeStorageDomainId(storageDomainId);
        if (!definitionMap.containsKey(normalizedStorageDomainId)) {
            throw new JsonException(404, "附属存储域不存在：" + normalizedStorageDomainId);
        }
        return storageMap.computeIfAbsent(
                normalizedStorageDomainId,
                id -> new DefaultAttachStorage(storeServiceFactory, StringUtils.appendPath(getAttachRootPath(), id))
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerStorageDomain(AttachStorageDomainDefinition definition) {
        if (definition == null) {
            throw new JsonException(400, "附属存储域定义不能为空");
        }
        AttachStorageDomainDefinition copiedDefinition = copyDefinition(definition);
        String normalizedStorageDomainId = normalizeStorageDomainId(copiedDefinition.getId());
        copiedDefinition.setId(normalizedStorageDomainId);
        AttachStorageDomainDefinition exists = definitionMap.putIfAbsent(normalizedStorageDomainId, copiedDefinition);
        if (exists != null) {
            throw new JsonException(400, "附属存储域已注册：" + normalizedStorageDomainId);
        }
        try {
            getStorageProvider().mkdirs(getStorageDomainRootPath(normalizedStorageDomainId));
            log.info("[Attach Storage]注册附属存储域：{}", normalizedStorageDomainId);
        } catch (IOException e) {
            definitionMap.remove(normalizedStorageDomainId);
            storageMap.remove(normalizedStorageDomainId);
            log.error("[Attach Storage]初始化附属存储域失败：{}", normalizedStorageDomainId, e);
            JsonException exception = new JsonException("初始化附属存储域失败：" + normalizedStorageDomainId + "，" + e.getMessage());
            exception.initCause(e);
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeStorageDomain(String storageDomainId) {
        String normalizedStorageDomainId = normalizeStorageDomainId(storageDomainId);
        definitionMap.remove(normalizedStorageDomainId);
        storageMap.remove(normalizedStorageDomainId);
        log.info("[Attach Storage]移除附属存储域：{}", normalizedStorageDomainId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AttachStorageDomainDefinition> listStorageDomain() {
        return definitionMap.values()
                .stream()
                .map(this::copyDefinition)
                .sorted(Comparator.comparing(AttachStorageDomainDefinition::getId))
                .toList();
    }

    /**
     * 获取当前生效的底层原始存储操作器。
     */
    private Storage getStorageProvider() {
        return storeServiceFactory.getService().getStorageProvider();
    }

    /**
     * 获取附属存储的公共根目录。
     */
    private String getAttachRootPath() {
        return StringUtils.appendPath(sysProperties.getStore().getRoot(), ATTACH_ROOT_DIR);
    }

    /**
     * 获取指定存储域的根目录。
     */
    private String getStorageDomainRootPath(String storageDomainId) {
        return StringUtils.appendPath(getAttachRootPath(), normalizeStorageDomainId(storageDomainId));
    }

    /**
     * 复制一份存储域定义，避免外部修改内部状态。
     */
    private AttachStorageDomainDefinition copyDefinition(AttachStorageDomainDefinition definition) {
        AttachStorageDomainDefinition copied = new AttachStorageDomainDefinition();
        copied.setId(definition.getId());
        copied.setName(definition.getName());
        copied.setDescription(definition.getDescription());
        return copied;
    }

    /**
     * 规范化并校验存储域标识。
     */
    private String normalizeStorageDomainId(String storageDomainId) {
        if (storageDomainId == null) {
            throw new JsonException(400, "附属存储域ID不能为空");
        }
        String normalizedStorageDomainId = storageDomainId.trim();
        if (normalizedStorageDomainId.isEmpty()) {
            throw new JsonException(400, "附属存储域ID不能为空");
        }
        if (!STORAGE_DOMAIN_ID_PATTERN.matcher(normalizedStorageDomainId).matches()) {
            throw new JsonException(400, "附属存储域ID包含非法字符：" + storageDomainId);
        }
        if (".".equals(normalizedStorageDomainId) || "..".equals(normalizedStorageDomainId)) {
            throw new JsonException(400, "附属存储域ID不合法：" + storageDomainId);
        }
        return normalizedStorageDomainId;
    }

}






