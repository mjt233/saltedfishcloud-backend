package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;

import java.util.List;
import java.util.Map;

/**
 * 存储工厂注册表。
 * <p>
 * 负责统一管理系统内可用的 {@link StorageFactory}，并按协议解析出对应的存储实现。
 * 当前处于渐进式重构阶段，因此 {@link #getStorage(String, Map)} 的返回值暂时仍保持为
 * {@link DiskFileSystem}，后续再按计划切换为更底层的存储接口。
 * </p>
 */
public interface StorageRegistry {
    /**
     * 注册一个新的存储工厂。
     *
     * @param factory 待注册的存储工厂
     */
    void registerStorageFactory(StorageFactory factory);

    /**
     * 获取所有对当前用户公开可用的存储工厂。
     *
     * @return 公开可用的存储工厂列表
     */
    List<StorageFactory> listPublicStorageFactory();

    /**
     * 获取所有已注册的存储工厂。
     *
     * @return 已注册的存储工厂列表
     */
    List<StorageFactory> listStorageFactory();

    /**
     * 根据协议和参数获取对应的存储实例。
     *
     * @param protocol 存储协议
     * @param params 参数
     * @return 存储实例（当前阶段暂时仍返回 {@link DiskFileSystem}）
     * @throws FileSystemParameterException 参数错误导致的存储获取失败
     */
    DiskFileSystem getStorage(String protocol, Map<String, Object> params) throws FileSystemParameterException;

    /**
     * 根据协议获取存储工厂。
     *
     * @param protocol 存储协议
     * @return 存储工厂
     */
    StorageFactory getStorageFactory(String protocol);

    /**
     * 判断是否存在支持指定协议的存储工厂。
     *
     * @param protocol 存储协议
     * @return 是否支持该协议
     */
    boolean isSupportedProtocol(String protocol);
}

