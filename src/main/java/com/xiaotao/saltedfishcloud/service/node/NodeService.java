package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.dao.NodeDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Service
public class NodeService {
    @Resource
    NodeDao nodeDao;

    /**
     * 使用路径取节点ID
     * @TODO 使用缓存加快查询速度
     * @param path 完整路径
     * @return 路径ID
     */
    public NodeInfo getNodeIdByPath(int uid, String path) {
        return getPathNodeByPath(uid, path).getLast();
    }

    /**
     * 获取某个路径中途径的节点信息
     * @param uid   用户ID
     * @param path  路径
     * @return  节点信息列表
     */
    public LinkedList<NodeInfo> getPathNodeByPath(int uid, String path) {
        LinkedList<NodeInfo> link = new LinkedList<>();
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        Collection<String> paths = pb.getPath();
        try {
            paths.forEach(node -> {
                String parent = link.isEmpty() ? "root" : link.getLast().getId();
                link.add(nodeDao.getNodeByParentId(uid, parent, node));
            });
            if (link.isEmpty()) {
                throw new NullPointerException();
            }
        } catch (NullPointerException e) {
            NodeInfo info = new NodeInfo();
            info.setId("root");
            link.add(info);
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
                id = SecureUtils.getMd5(name + new Date() + Math.random());
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
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return          完整路径
     */
    public String getPathByNode(int uid, String nodeId) {
        if (nodeId.equals("root")) {
            return "/";
        }
        LinkedList<String> link = new LinkedList<>();
        String lastId = nodeId;
        NodeInfo info;

        // 递归查询
        while ( (info = nodeDao.getNodeById(uid, lastId)) != null) {
            link.addFirst(info.getName());
            lastId = info.getParent();
            if (info.getParent().equals("root")) {
                break;
            }
        }
        if (link.isEmpty()) {
            throw new HasResultException(404, "无效的nodeId");
        }
        StringBuilder stringBuilder = new StringBuilder();
        link.forEach(name -> {
            stringBuilder.append("/").append(name);
        });
        return stringBuilder.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public void test() {
        nodeDao.addNode(233, "test", "test", "test");
    }
}
