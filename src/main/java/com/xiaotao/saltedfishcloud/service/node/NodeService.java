package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.dao.NodeDao;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.utils.PathBuilder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.stereotype.Service;

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
    public String getNodeIdByPath(String path) {
        LinkedList<NodeInfo> link = new LinkedList<>();
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        Collection<String> paths = pb.getPath();
        paths.forEach(node -> {
            String parent = link.isEmpty() ? "root" : link.getLast().getId();
            link.add(nodeDao.getNodeByParentId(parent, node));
        });
        if (link.isEmpty()) {
            return "root";
        } else {
            return link.getLast().getId();
        }
    }

    /**
     * 添加一个节点
     * @param name 名称
     * @param parent 父节点ID
     * @return 新节点ID
     */
    public String addNode(String name, String parent) {
        int i;
        String id;
        NodeInfo node = nodeDao.getNodeByParentId(parent, name);
        if (node != null) {
            return node.getId();
        } else {
            do {
                id = SecureUtils.getMd5(name + new Date() + Math.random());
                i = nodeDao.addNode(name, id, parent);
            } while (i == 0);
            return id;
        }
    }

    /**
     * 取某节点下的所有子节点
     * @return 某节点下的所有子节点（不包含自己）
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
            nodes = nodeDao.getUserChildNodesByMulti(uid, ids);
            res.addAll(nodes);
            ids.clear();
            nodes.forEach(nodeInfo -> {
                ids.add(nodeInfo.getId());
            });
        } while (!nodes.isEmpty());
        return res;
    }
}
