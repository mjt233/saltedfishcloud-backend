package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.dao.jpa.NodeInfoRepo;
import com.xiaotao.saltedfishcloud.dao.mybatis.NodeDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 节点服务，用于管理目录存储节点相关信息
 * 缓存key（停用）：
 * path::{uid}:node:{nid}                   通过节点ID查询节点信息（NodeInfo）的缓存
 * path::{uid}:pnid:{parentId}:{nodeName}   通过父节点ID和节点名称查询节点信息（NodeInfo）的缓存
 * path::{uid}:path:{nodeId}                通过节点ID查询路径
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NodeServiceImpl implements NodeService {
    private final static String LOG_PREFIX = "[Node]";
    private final NodeDao nodeDao;
    @Autowired
    private NodeInfoRepo nodeInfoRepo;

//    @Autowired
//    private NodeCacheService cacheService;

//    @Autowired
//    private NodeService self;

    @Override
//    @Cacheable(cacheNames = CacheNames.PATH, key = "#uid+':node:'+#nid")
    public NodeInfo getNodeById(Long uid, String nid) {
        if (nid.length() == 32) {
            return nodeDao.getNodeById(uid, nid);
        }
        NodeInfo node = new NodeInfo();
        node.setId(uid + "");
        node.setUid(uid);
        return node;
    }

    @Override
    public NodeTree getFullTree(long uid) {
        NodeTree tree = new NodeTree();
        List<NodeInfo> allNode = nodeDao.getAllNode(uid);
        tree.putNode(NodeInfo.getRootNode(uid));
        if (allNode != null) {
            allNode.forEach(tree::putNode);
        }
        return tree;
    }

    @Override
    public NodeInfo getNodeByPath(long uid, String path) {
        try {
            return getPathNodeByPath(uid, path).getLast();
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Nullable
    @Override
    public List<NodeInfo> listNodeByNodeId(long uid, String nodeId) {
        NodeInfo example = new NodeInfo();
        example.setUid(uid);
        example.setParent(nodeId);
        return nodeInfoRepo.findAll(Example.of(example));
    }

    @Override
//    @Cacheable(cacheNames = CacheNames.PATH, key = "#uid+':pnid:'+#parentId+':'+#nodeName")
    public NodeInfo getNodeByParentId(long uid, String parentId, String nodeName) {
        return nodeDao.getNodeByParentId(uid, parentId, nodeName);
    }

    @Override
    public Deque<NodeInfo> getPathNodeByPathNoEx(long uid, String path) {
        log.debug("{}<<==== 开始解析路径途径节点 uid: {} 路径: {}",LOG_PREFIX, uid, path);
        Deque<NodeInfo> link = new ArrayDeque<>();
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        Collection<String> paths = pb.getPath();
        Set<String> visited = new HashSet<>();

        String strId = "" + uid;
        for (String node : paths) {
            String parent = link.isEmpty() ? strId : link.getLast().getId();
            NodeInfo info = this.getNodeByParentId(uid, parent, node);
            if (info == null) {
                log.warn("{}路径不存在:{}", LOG_PREFIX, path);
                return null;
            }
            if (visited.contains(info.getId())) {
                throw new JsonException(500, "出现文件夹循环包含，请联系管理员并提供以下信息：uid=" + uid + " " + info.getId() + " => " + node);
            } else {
                visited.add(node);
                link.add(info);
            }
        }
        if (link.isEmpty()) {
            NodeInfo info = new NodeInfo();
            info.setId(strId);
            info.setUid(uid);
            link.add(info);
        }
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            int cnt = 0;
            for (NodeInfo nodeInfo : link) {
                log.debug("{}途径节点[{}]: {}", LOG_PREFIX, cnt++, nodeInfo);
                sb.append("/").append(nodeInfo.getName() == null ? "" : nodeInfo.getName()).append('[').append(nodeInfo.getId()).append(']');
            }
            log.debug("{}====>> 解析成功 路径节点信息: {}",LOG_PREFIX, sb);
        }
        return link;
    }

    @Override
    public Deque<NodeInfo> getPathNodeByPath(long uid, String path) throws NoSuchFileException {
        Deque<NodeInfo> list = getPathNodeByPathNoEx(uid, path);
        if (list == null) {
            throw new NoSuchFileException("路径" + path + "不存在，或节点信息已丢失");
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addNode(long uid, String name, String parent) {
        return addNode(uid, name, parent, false);
    }

    @Override
    public String addNode(long uid, String name, String parent, Boolean isMount) {
        int i;
        String id;
//        cacheService.deletePnidCache(uid, parent, name);
        NodeInfo node = nodeDao.getNodeByParentId(uid, parent, name);
        if (node != null) {
            return node.getId();
        } else {
            do {
                id = SecureUtils.getUUID();
                i = nodeDao.addNode(uid, name, id, parent, isMount);
            } while (i == 0);
            return id;
        }
    }

    @Override
    public List<NodeInfo> getChildNodes(long uid, String nid) {
        // 最终结果
        List<NodeInfo> res = new ArrayList<>();

        // 单次查询得到的节点集合
        List<NodeInfo> nodes;
        Map<String, String> nodePathMap = new HashMap<>();
        nodePathMap.put(nid, getPathByNode(uid, nid));

        // nodes中的id部分
        List<String> ids = new ArrayList<>();
        ids.add(nid);
        do {
            nodes = nodeInfoRepo.findByUidAndParent(uid, ids);
            res.addAll(nodes);
            ids = nodes.stream()
                    .peek(node -> {
                        node.setPath(nodePathMap.get(node.getParent()));
                        nodePathMap.put(node.getId(), node.getPath());
                    })
                    .map(NodeInfo::getId)
                    .collect(Collectors.toList());
        } while (!nodes.isEmpty());
        return res;
    }


    @Override
//    @RemoveNodeCache(uid = 0, nid = 1)
    public int deleteNodes(long uid, Collection<String> ids) {
        if (!ids.isEmpty()) {
            return nodeInfoRepo.deleteByIdAndUid(uid, ids);
        } else {
            return 0;
        }
    }

    @Override
    public String getNodeIdByPath(long uid, String path) throws NoSuchFileException {
        return this.getPathNodeByPath(uid, path).getLast().getId();
    }

    @Override
    public String getNodeIdByPathNoEx(long uid, String path) {
        Deque<NodeInfo> list = this.getPathNodeByPathNoEx(uid, path);
        if (list == null) {
            return null;
        }
        return list.getLast().getId();
    }

    @Override
//    @Cacheable(cacheNames = CacheNames.PATH, key = "#uid+':path:'+#nodeId")
    public String getPathByNode(long uid, String nodeId) {
        return "/" + listAllParentByNodeId(uid, nodeId).stream()
                .map(NodeInfo::getName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining("/"));
    }

    @Override
    public List<NodeInfo> listAllParentByNodeId(long uid, String nodeId) {

        if (nodeId.length() < 32) {
            return Collections.singletonList(NodeInfo.getRootNode(uid));
        }
        List<NodeInfo> res = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        String lastId = nodeId;
        NodeInfo info;
        visited.add(nodeId);

        // 迭代查询
        while ( (info =  this.getNodeById(uid, lastId)) != null) {
            res.add(info);
            lastId = info.getParent();
            if (visited.contains(lastId)) {
                throw new JsonException(500, "出现文件夹循环包含，请联系管理员并提供以下信息：uid=" + uid + " " + info.getId() + " => " + lastId);
            }
            if (info.getParent().length() < 32) {
                break;
            }
        }
        if (res.isEmpty()) {
            throw new JsonException(404, "无效的nodeId");
        }
        res.add(NodeInfo.getRootNode(uid));
        Collections.reverse(res);
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addMountPointNode(MountPoint mountPoint) {
        NodeInfo nodeInfo = NodeInfo.builder()
                .mountId(mountPoint.getId())
                .name(mountPoint.getName())
                .uid(mountPoint.getUid())
                .parent(mountPoint.getNid())
                .build();
        nodeInfoRepo.save(nodeInfo);
        return nodeInfo.getId();
    }
}
