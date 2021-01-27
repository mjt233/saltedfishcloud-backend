package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.PathMapDao;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class PathMapService {
    @Resource
    private PathMapDao pathMapDao;

    /**
     * 设置一条路径ID映射记录 若已存在则会被忽略
     * @param path 路径全名
     * @param nid  路径ID
     * @return 影响的行数
     */
    public int setRecord(String path, String nid) {
        return pathMapDao.addPathRecord(nid, path);
    }

    /**
     * 设置一条路径ID映射记录 若已存在则会被忽略
     * @param path 路径全名
     * @return 影响的行数
     */
    public int setRecord(String path) {
        return pathMapDao.addPathRecord(SecureUtils.getMd5(path), path);
    }

    /**
     * 尝试删除一条路径ID映射记录 若仍被依赖则忽略
     * @param nid 路径节点ID
     * @return 影响的行数
     */
    public int deleteRecord(String nid) {
        return pathMapDao.removePathRecord(nid);
    }
}
