package com.sfc.quickshare.service;

import com.sfc.quickshare.model.QuickShare;
import com.sfc.quickshare.model.QuickShareProperty;
import com.sfc.quickshare.repo.QuickShareRepo;
import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class QuickShareService {
    private final static String LOG_PREFIX = "[快速分享]";
    private final static String PREFIX_DIR = "quick_share";

    @Autowired
    @Getter
    private QuickShareRepo repo;

    @Autowired
    private StoreServiceFactory storeServiceFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private QuickShareProperty property;

    private String getRedisKey(String code) {
        return "quick_share::" + code;
    }

    /**
     * 录入提取码
     * @param code  提取码
     * @param id    快速分享id
     * @return      实际录入的提取码
     */
    private String saveCode(String code, Long id) {
        String actualCode = code.toLowerCase();
        Duration duration = Duration.ofMinutes(property.getEffectiveDuration());
        while (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(this.getRedisKey(actualCode), id, duration))) {
            actualCode = StringUtils.getRandomString(5, false);
        }
        return actualCode;
    }


    /**
     * 保存临时文件
     * @param resource      待保持文件资源
     * @param quickShare    快速分享对象
     * @return 该文件的文件提取码
     * @throws IOException  IO异常
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveTempFile(Resource resource, QuickShare quickShare) throws IOException {
        this.checkIsEnable();
        long fileSize = resource.contentLength();
        this.checkSize(fileSize);

        // 设置基础数据
        quickShare.setId(IdUtil.getId());
        quickShare.setFileName(resource.getFilename());
        quickShare.setSize(fileSize);

        // 设置基础数据 - 标记过期日期
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, property.getEffectiveDuration());
        quickShare.setExpiredAt(calendar.getTime());

        // 存储文件
        try(InputStream is = resource.getInputStream()) {
            storeServiceFactory.getTempStoreService().store(FileInfo.getFromResource(resource, quickShare.getUid(), FileInfo.TYPE_FILE), StringUtils.appendPath(PREFIX_DIR, quickShare.getId().toString()), fileSize, is);
        }

        // 生成并记录提取码
        String actualCode = this.saveCode(quickShare.getCode(), quickShare.getId());
        quickShare.setCode(actualCode);

        // 存库
        repo.save(quickShare);

        return actualCode;
    }


    /**
     * 根据文件提取码获取文件快速分享id
     * @param code  文件提取码
     * @return      如果提取码不存在或失效，则为null，
     */
    private Long getIdByCode(String code) {
        Object id = redisTemplate.opsForValue().get(getRedisKey(code));
        if (id == null) {
            return null;
        } else {
            return TypeUtils.toLong(id);
        }
    }

    /**
     * 获取快速分享文件的在临时存储服务上的存储路径
     * @param id    分享id
     */
    private String getFilePath(Long id) {
        return StringUtils.appendPath(PREFIX_DIR, id.toString());
    }

    /**
     * 获取快速分享文件的文件存储服务
     */
    private DirectRawStoreHandler getStoreService() {
        return storeServiceFactory.getTempStoreService();
    }

    /**
     * 在存储上移除快速分享文件
     * @param id    快速分享id
     */
    public void deleteFile(Long id) throws IOException {
        this.getStoreService().delete(this.getFilePath(id));
    }

    /**
     * 清理所有过期文件
     */
    public void cleanExpiredFiles() throws IOException {
        // 筛选出过期的快速分享
        List<QuickShare> allExpired = repo.findAllExpired(new Date());
        if (allExpired != null && !allExpired.isEmpty()) {
            List<QuickShare> successList = new ArrayList<>(allExpired.size());

            // 在存储服务上删除文件
            for (QuickShare quickShare : allExpired) {
                try {
                    deleteFile(quickShare.getId());
                    log.info("{}清理过期文件：{}", LOG_PREFIX, quickShare.getId());
                    successList.add(quickShare);
                } catch (Throwable e) {
                    log.error("{}过期文件{}清理失败", LOG_PREFIX, quickShare.getId(), e);
                }
            }

            // 从数据库中移除删除的记录
            if (!successList.isEmpty()) {
                repo.deleteAllInBatch(successList);
            }

        }
    }

    /**
     * 根据分享id获取快速分享的文件
     * @param id    分享id
     * @return      文件资源
     */
    public Resource getFileById(Long id) throws IOException {
        this.checkIsEnable();
        Resource resource = this.getStoreService().getResource(this.getFilePath(id));
        if (resource == null || !resource.exists()) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        return resource;
    }

    private void checkIsEnable() {
        if (!Boolean.TRUE.equals(property.getIsEnabled())) {
            throw new IllegalArgumentException("功能未开启");
        }
    }

    private void checkSize(long fileSize) {
        long maxSize = property.getMaxSize() * ByteSize._1MiB;
        if (fileSize > maxSize) {
            throw new IllegalArgumentException("文件不能大于" + StringUtils.getFormatSize(maxSize));
        }
    }

    /**
     * 根据提取码获取分享信息
     * @param code  提取码
     * @return      分享信息，若不存在则抛出异常
     */
    public QuickShare getByCode(String code) {
        this.checkIsEnable();
        Long id = getIdByCode(code);
        if (id == null) {
            throw new JsonException("提取码不存在或文件已过期");
        }
        return repo.findById(id).orElseThrow(() -> new JsonException("文件已过期或记录失效"));
    }


}
