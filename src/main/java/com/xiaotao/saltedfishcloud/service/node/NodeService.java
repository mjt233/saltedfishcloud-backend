package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.dao.NodeDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.file.NoSuchFileException;
import java.util.*;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class NodeService {
    @Resource
    NodeDao nodeDao;

    public NodeTree getFullTree(int uid) {
        NodeTree tree = new NodeTree();
        List<NodeInfo> allNode = nodeDao.getAllNode(uid);
        tree.putNode(NodeInfo.getRootNode(uid));
        if (allNode != null) {
            allNode.forEach(tree::putNode);
        }
        return tree;
    }

    /**
     * 使用路径取最后一个节点的信息
     * @TODO 使用缓存加快查询速度
     * @param path 完整路径
     * @throws NoSuchFileException 请求的路径不存在时抛出此异常
     * @return 路径ID
     */
    public NodeInfo getLastNodeInfoByPath(int uid, String path) throws NoSuchFileException {
        return getPathNodeByPath(uid, path).getLast();
    }

    /**
     * 获取某个路径中途径的节点信息
     * @param uid   用户ID
     * @param path  路径
     * @throws NoSuchFileException 请求的路径不存在时抛出此异常
     * @return  节点信息列表
     */
    public LinkedList<NodeInfo> getPathNodeByPath(int uid, String path) throws NoSuchFileException {
        log.debug("search path" + uid + ": " + path);
        LinkedList<NodeInfo> link = new LinkedList<>();
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        Collection<String> paths = pb.getPath();
        Set<String> visited = new HashSet<>();
        try {
            for (String node : paths) {
                String parent = link.isEmpty() ? "root" : link.getLast().getId();
                NodeInfo info = nodeDao.getNodeByParentId(uid, parent, node);
                if (info == null) {
                    throw new NoSuchFileException("路径 " + path + " 不存在，或目标节点信息已丢失");
                }
                if (visited.contains(info.getId())) {
                    throw new HasResultException(500, "出现文件夹循环包含，请联系管理员并提供以下信息：uid=" + uid + " " + info.getId() + " => " + node);
                } else {
                    visited.add(node);
                    link.add(info);
                }
            }
            if (link.isEmpty()) {
                throw new NullPointerException();
            }
        } catch (NullPointerException e) {
            NodeInfo info = new NodeInfo();
            info.setId("root");
            link.add(info);
        }
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (NodeInfo nodeInfo : link) {
                log.debug("nodeInfo:" + nodeInfo);
                sb.append("/").append(nodeInfo.getName() == null ? "" : nodeInfo.getName()).append('[').append(nodeInfo.getId()).append(']');
            }
            log.debug(sb.toString());
        }
        return link;
    }

    /**
     * 添加一个节点
     * @param name 名称
     * @param parent 父节点ID
     * @return 新节点ID
     */
    public String addNode(int uid, String name, String parent) {
        int i;
        String id;
        NodeInfo node = nodeDao.getNodeByParentId(uid, parent, name);
        if (node != null) {
            return node.getId();
        } else {
            do {
                id = SecureUtils.getUUID();
                i = nodeDao.addNode(uid, name, id, parent);
            } while (i == 0);
            return id;
        }
    }

    /**
     * 取某节点下的所有子节点
     * @param uid 用户ID
     * @param nid 目标节点ID
     * @return 目标节点下的所有子节点（不包含自己）
     */
    public List<NodeInfo> getChildNodes(int uid, String nid) {
        // 最终结果
        List<NodeInfo> res = new LinkedList<>();

        // 单次查询得到的节点集合
        List<NodeInfo> nodes;

        // nodes中的id部分
        List<String> ids = new LinkedList<>();
        ids.add(nid);
        do {
            nodes = nodeDao.getChildNodes(uid, ids);
            res.addAll(nodes);
            ids.clear();
            nodes.forEach(nodeInfo -> ids.add(nodeInfo.getId()));
        } while (!nodes.isEmpty());
        return res;
    }

    public int deleteNodes(int uid, Collection<String> ids) {
        if (!ids.isEmpty()) {
            return nodeDao.deleteNodes(uid, ids);
        } else {
            return 0;
        }
    }

    /**
     * 通过节点ID 获取节点所在的完整路径位置
     * @TODO 使用缓存优化查询速度
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return          完整路径
     */
    public String getPathByNode(int uid, String nodeId) {
        if (nodeId.equals("root")) {
            return "/";
        }
        LinkedList<String> link = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        String lastId = nodeId;
        NodeInfo info;
        visited.add(nodeId);

        // 递归查询
        while ( (info = nodeDao.getNodeById(uid, lastId)) != null) {
            link.addFirst(info.getName());
            lastId = info.getParent();
            if (visited.contains(lastId)) {
                throw new HasResultException(500, "出现文件夹循环包含，请联系管理员并提供以下信息：uid=" + uid + " " + info.getId() + " => " + lastId);
            }
            if (info.getParent().equals("root")) {
                break;
            }
        }
        if (link.isEmpty()) {
            throw new HasResultException(404, "无效的nodeId");
        }
        StringBuilder stringBuilder = new StringBuilder();
        link.forEach(name -> stringBuilder.append("/").append(name));
        return stringBuilder.toString();
    }

    public void test() {
        nodeDao.addNode(233, "test", "test", "test");
    }
}
