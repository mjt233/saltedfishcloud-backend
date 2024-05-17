package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;

import java.util.Collection;
import java.util.List;

/**
 * 增删改查服务
 * @param <T> 实体类
 */
public interface CrudService<T> {
    /**
     * 按id查询数据
     * @param id    数据id
     * @return      数据对象
     */
    T findById(Long id);

    /**
     * 保存实体对象信息
     * @param entity    要保存的对象
     */
    void save(T entity);

    /**
     * 使用带持有人权限验证的保存，默认实现中不允许非管理员对公共数据操作，需要额外限制重写该方法即可。
     * @param entity    待保存的实体类
     */
    void saveWithOwnerPermissions(T entity);

    /**
     * 带数据拥有者权限验证的删除数据
     * @param id    待保存的数据id
     */
    void deleteWithOwnerPermissions(Long id);

    /**
     * 批量保存
     * @param entityList    要保存的对象列表
     */
    void batchSave(Collection<T> entityList);

    /**
     * 查询所有数据
     * @return  所有数据
     */
    List<T> findAll();

    /**
     * 按用户id分页查询数据
     * @param uid               用户id
     * @param pageableRequest   分页查询参数，为null则表示不分页
     * @return                  分页查询结果
     */
    CommonPageInfo<T> findByUid(Long uid, PageableRequest pageableRequest);

    /**
     * 带数据拥有者权限验证的按用户id分页查询数据。默认实现中允许非管理员对公共数据操作，需要额外限制重写该方法即可。
     * @param uid               用户id
     * @param pageableRequest   分页查询参数，为null则表示不分页
     * @return                  分页查询结果
     */
    CommonPageInfo<T> findByUidWithOwnerPermissions(Long uid, PageableRequest pageableRequest);

    /**
     * 按用户id查询数据
     * @param uid               用户id
     * @return                  查询结果
     */
    List<T> findByUid(Long uid);

    /**
     * 按id删除一条数据
     * @param id    数据id
     */
    void delete(Long id);

    /**
     * 按id批量删除数据
     * @param ids       待删除的数据id
     * @return          删除数量
     */
    int batchDelete(Collection<Long> ids);


    /**
     * 批量插入，比saveAll性能好
     */
    void batchInsert(Iterable<T> entityList);
}
