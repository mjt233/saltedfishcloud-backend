package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.PathInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PathMapDao {
    /**
     * 使用路径节点ID获取路径全名
     * @param id 路径ID
     * @return 路径全名
     */
    @Select("SELECT path from path_map where id=#{id}")
    String getPathById(@Param("id") String id);


    /**
     * 添加一条路径节点ID与路径全名的映射记录
     * @param id 路径ID
     * @param path 路径全名
     * @return 影响的行数
     */
    @Insert("INSERT IGNORE INTO path_map (id,path) VALUES (#{id},#{path})")
    int addPathRecord(@Param("id") String id,
                      @Param("path") String path);

    /**
     * 删除一条映射记录，会忽略掉仍引用的路径
     * @param id 映射ID
     * @return 影响的行数
     */
    @Delete("DELETE FROM path_map WHERE id=#{id} AND id not in (SELECT node FROM file_table WHERE id=#{id}) AND path != '/'")
    int removePathRecord(@Param("id") String id);

    /**
     * 批量删除路径映射记录，会忽略掉仍被引用的路径
     * @param nodes 节点ID列表
     * @return 影响的行数
     */
    @Delete({
            "<script>",
            "DELETE FROM path_map WHERE id in ",
                "<foreach collection='nodes' item='id' open='(' separator=',' close=')'>",
                "#{id}",
                "</foreach>",
            " AND id not in (SELECT node FROM file_table GROUP BY node) AND path != '/'",
            "</script>"
    })
    int removePathsRecord(@Param("nodes") List<String> nodes);

    /**
     * 获取路径映射的引用计数
     * @param nid 节点ID
     * @return 计数
     */
    @Select("SELECT COUNT(id) count FROM path_map WHERE id=#{nid}")
    int getReferenceCount(@Param("nid") String nid);

    /**
     * 获取某个目录下的所有子目录的节点ID
     * @param root 根节点ID
     * @return 所有节点ID
     */
    @Select("SELECT id,path FROM path_map WHERE path = #{root} OR path like concat(#{root}, '/%')")
    List<PathInfo> getDirTreeIds(@Param("root")String root);
}
