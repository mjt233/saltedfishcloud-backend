package com.xiaotao.saltedfishcloud.service.share.dao;

import com.xiaotao.saltedfishcloud.service.share.entity.SharePO;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareDao extends JpaRepository<SharePO, Integer> {
//
//    @Query(value = "SELECT " +
//            "s.id, s.verification, s.uid, s.nid, s.parent_id, s.type, s.size, s.extract_code, s.name, s.created_at, expired_at, u.user as username " +
//            "FROM share AS s, user as u WHERE s.uid = ?1 AND s.uid = u.id ORDER BY s.created_at DESC", nativeQuery = true)
//    Page<SharePO> findAllByUidEquals(Integer uid, Pageable page);

//    @Query(nativeQuery = true, value = "SELECT " +
//            "share.id, share.verification, share.uid, share.nid, share.parent_id, share.type, share.size, share.extract_code, share.name, share.created_at, expired_at, user.user as username " +
//            "FROM share JOIN user ON user.id = share.uid WHERE share.id = ?1")
//    Optional<SharePO> findById(Integer id);
}
