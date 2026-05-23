package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;

import java.util.Collection;
import java.util.Map;

public interface StorageFactory {
    /**
     * 根据给定的参数获取对应的存储实例。
     * <p>
     * 返回值应当已经完成挂载根路径作用域包装，调用方无需再自行处理基础路径拼接与越界访问防护。
     * </p>
     *
     * @param params    参数map
     * @return  对应的存储实例
     * @throws FileSystemParameterException 参数错误导致的存储获取失败
     */
    Storage getStorage(Map<String, Object> params) throws FileSystemParameterException;

    /**
     * 测试存储是否正常。
     *
     * @param storage 待测试的存储实例
     */
    void testStorage(Storage storage) throws FileSystemParameterException;

    /**
     * 清理无用存储缓存，会被定时任务定时调用，以达到通知清理缓存和释放不再使用的远程连接的目的。
     *
     * @param params 可以保留的存储参数
     */
    default void clearCache(Collection<Map<String, Object>> params) {

    }

    /**
     * 清理指定的完全匹配参数下创建的存储缓存。
     *
     * @param params 存储参数
     */
    default void clearCache(Map<String, Object> params) {

    }

    /**
     * 获取该存储工厂的描述信息。
     *
     * @return  协议名称
     */
    StorageMetadata getMetadata();
}
