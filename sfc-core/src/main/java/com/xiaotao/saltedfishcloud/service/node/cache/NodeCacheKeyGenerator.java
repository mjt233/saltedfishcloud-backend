package com.xiaotao.saltedfishcloud.service.node.cache;

public class NodeCacheKeyGenerator {

    public static String generatePnidKey(long uid, String nid, String name) {
        return "path::" + uid + ":pnid:" + nid + ":" + name;
    }

}
