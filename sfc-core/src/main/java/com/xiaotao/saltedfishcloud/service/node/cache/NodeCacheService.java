package com.xiaotao.saltedfishcloud.service.node.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 节点信息缓存服务
 * 缓存key：
 * path::{uid}:node:{nid}                   通过节点ID查询节点信息（NodeInfo）的缓存
 * path::{uid}:pnid:{parentId}:{nodeName}   通过父节点ID和节点名称查询节点信息（NodeInfo）的缓存
 * path::{uid}:path:{nodeId}                通过节点ID查询路径
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class NodeCacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisDao redisDao;
    private final NodeService nodeService;

    public void deleteNodeCache(int uid, Collection<String> nodeIds) {
        try {
            deleteNodeCache(uid, nodeIds, true);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除一个由父节点ID和节点名称指向节点信息的缓存
     * @param uid       用户ID
     * @param parentId  父节点ID
     * @param nodeName  节点名称
     */
    public void deletePnidCache(int uid, String parentId, String nodeName) {
        redisTemplate.delete(NodeCacheKeyGenerator.generatePnidKey(uid, parentId, nodeName));
    }

    /**
     * 删除节点缓存
     * @param uid       用户ID
     * @param nodeIds   要被删除的节点ID列表
     */
    private void deleteNodeCache(int uid, Collection<String> nodeIds, boolean queryParent) throws JsonProcessingException {
        if (nodeIds.size() == 0) return;
        log.debug("[NodeCache]<<==== 开始删除节点缓存 uid:{} 节点id集合: {}", uid, nodeIds);

        List<String> keys = new LinkedList<>();
        for (String nid : nodeIds) {
            // nid的所有子节点的缓存key集合
            Set<String> subNodeKeys = redisDao.scanKeys(NodeCacheKeyGenerator.generatePnidKey(uid, nid, "*"));
            List<String> subids = new LinkedList<>();
            for (String subNode : subNodeKeys) {
                // 获取nid的子节点的id，添加到后续需要删除的id列表，并移除subNode节点在父节点中的名称缓存
                NodeInfo n = MapperHolder.mapper.readValue(redisTemplate.opsForValue().get(subNode), NodeInfo.class);
                subids.add(n.getId());
                redisTemplate.delete(NodeCacheKeyGenerator.generatePnidKey(uid, nid, n.getName()));
            }

            // 继续对查找出的子节点进行删除操作
            if (!subids.isEmpty()) {
                deleteNodeCache(uid, subids ,false);
            }

            /* 因传入的仅仅为节点的ID，而没有该节点的名称和父节点ID，缓存中可能依然存在
             * 该节点的pnid记录（通过父节点和节点名称获取节点信息）需要清除。
             *
             * 一般deleteNodeCache方法的初始调用才需要设置queryParent为true去查询数据库，因为
             * 由最初的节点id搜索查找得到的子节点id在进入下一轮递归之前就已删除了.
             */
            if (queryParent) {
                NodeInfo nodeInfo = nodeService.getNodeById(uid, nid);
                redisTemplate.delete(NodeCacheKeyGenerator.generatePnidKey(uid, nodeInfo.getParent(), nodeInfo.getName()));
            }
            keys.add("path::" + uid + ":node:" + nid);
            keys.add("path::" + uid + ":path:" + nid);
            redisTemplate.delete(subNodeKeys);
        }
        redisTemplate.delete(keys);
        log.debug("[NodeCache]====>> 节点缓存清理完成 被移除的节点id集合：{}", nodeIds);
    }

    /**
     * 获取路径到节点ID的缓存key
     * @param uid   用户ID
     * @param path  请求的路径
     * @return  redis key
     */
    public static String getPathCacheKey(int uid, String path) {
        return "xyy::path::" + uid + "::" + path;
    }

    /**
     * 获取节点ID到路径的缓存key
     * @param uid   用户ID
     * @param id  请求的id
     * @return  redis key
     */
    public static String getNodeIdCacheKey(int uid, String id) {
        return "xyy::node::" + uid + "::" + id;
    }
}
