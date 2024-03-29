package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 集群节点信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClusterNodeInfo {
    /**
     * 实例id标识
     */
    private Long id;

    /**
     * 主机名
     */
    private String host;

    /**
     * ip地址
     */
    private String ip;

    /**
     * http服务端口
     */
    private Integer httpPort;

    /**
     * 内存字节容量
     */
    private Long memory;

    /**
     * CPU核心数
     */
    private Integer cpu;

    /**
     * 临时空间大小
     */
    private Long tempSpace;

    /**
     * 获取请求该节点http服务的url
     * @param url   接口路径
     * @return      完整url
     */
    public String getRequestUrl(String url) {
        return StringUtils.appendPath("http://" + this.getHost() + ":" + this.getHttpPort(), url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClusterNodeInfo that = (ClusterNodeInfo) o;

        if (!Objects.equals(host, that.host)) return false;
        if (!Objects.equals(ip, that.ip)) return false;
        return Objects.equals(cpu, that.cpu);
    }

    @Override
    public int hashCode() {
        int result = host != null ? host.hashCode() : 0;
        result = 31 * result + (ip != null ? ip.hashCode() : 0);
        result = 31 * result + (cpu != null ? cpu.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "{host=" + host + " ip:" + ip + "}";
    }
}
