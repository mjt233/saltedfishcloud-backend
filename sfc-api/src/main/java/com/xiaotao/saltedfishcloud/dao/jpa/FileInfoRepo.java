package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.projection.FileInfoSearchResult;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface FileInfoRepo extends BaseRepo<FileInfo> {
    @Query("""
        SELECT
            f as fileInfo,
            f2.name as parent
        FROM FileInfo f
        LEFT JOIN FileInfo f2 ON f.node = f2.md5
        WHERE f.uid = :uid
        AND f.name
        LIKE CONCAT('%', :key, '%')
    """)
    Page<FileInfoSearchResult> search(@Param("uid") Long uid, @Param("key") String key, Pageable pageable);

    @Query("SELECT SUM(f.size) FROM FileInfo f WHERE f.uid = :uid AND f.size > 0 AND (f.isMount = false OR f.isMount IS NULL)")
    Long getFileUsage(@Param("uid") Long uid);

    @Query("SELECT f FROM FileInfo f WHERE f.uid = :uid AND f.node = :node AND f.name = :name")
    FileInfo findFileInfo(@Param("uid") Long uid, @Param("name") String name, @Param("node") String node);

    @Query("SELECT f FROM FileInfo f WHERE f.uid = :uid AND f.node = :node AND f.name IN :names")
    List<FileInfo> findFileInfoByNames(@Param("uid") Long uid, @Param("names") Collection<String> names, @Param("node") String node);

    @Query("SELECT f FROM FileInfo f WHERE f.md5 = :md5 ORDER BY f.isMount DESC")
    Page<FileInfo> findByMd5(@Param("md5") String md5, Pageable pageable);

    @Query("SELECT DISTINCT f.md5 FROM FileInfo f WHERE f.md5 IN :md5List")
    Set<String> findByMd5List(@Param("md5List") Collection<String> md5List);

    List<FileInfo> findByUidAndNode(Long uid, String node);

    @Query("SELECT f FROM FileInfo f WHERE f.uid = :uid AND f.md5 = :md5 AND f.size = -1")
    FileInfo findDirByUidAndMd5(Long uid, String md5);

    /**
     * 获取指定目录节点下的所有目录信息
     * @param uid   用户id
     * @param node  目录节点id(FileInfo#getMd5)
     */
    @Query("SELECT f FROM FileInfo f WHERE f.node = :node AND f.uid = :uid AND f.size = -1")
    List<FileInfo> findDirByUidAndNode(@Param("uid") Long uid, @Param("node") String node);

    @Query("DELETE FROM FileInfo WHERE uid = :uid AND node = :node AND name IN :names")
    @Modifying
    @Transactional
    int deleteFiles(@Param("uid") Long uid,@Param("node") String node,@Param("names") Collection<String> names);

    @Query("DELETE FROM FileInfo WHERE uid = :uid AND node IN :nodes")
    @Modifying
    @Transactional
    int deleteByUidAndNode(@Param("uid") Long uid, @Param("nodes") Collection<String> nodes);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileInfo f WHERE f.size <> -1 AND f.uid <> 0 AND (f.isMount IS NULL OR f.isMount = false)")
    Long getUserTotalSize();

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileInfo f WHERE f.size <> -1 AND f.uid = 0 AND (f.isMount IS NULL OR f.isMount = false)")
    Long getPublicTotalSize();

    @Query("SELECT COUNT(f) FROM FileInfo f WHERE f.uid = 0 AND f.size = -1 AND (f.isMount IS NULL OR f.isMount = false)")
    Long getPublicDirCount();

    @Query("SELECT COUNT(f) FROM FileInfo f WHERE f.uid <> 0 AND f.size = -1 AND (f.isMount IS NULL OR f.isMount = false)")
    Long getUserDirCount();

    @Query("SELECT COUNT(f) FROM FileInfo f WHERE f.uid = 0 AND f.size <> -1 AND (f.isMount IS NULL OR f.isMount = false)")
    Long getPublicFileCount();

    @Query("SELECT COUNT(f) FROM FileInfo f WHERE f.uid <> 0 AND f.size <> -1 AND (f.isMount IS NULL OR f.isMount = false)")
    Long getUserFileCount();
}
