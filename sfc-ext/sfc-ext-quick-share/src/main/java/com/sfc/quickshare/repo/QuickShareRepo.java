package com.sfc.quickshare.repo;

import com.sfc.quickshare.model.QuickShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

/**
 * 针对表快速分享（quick_share）的repo
 */
public interface QuickShareRepo extends JpaRepository<QuickShare, Long> {
    @Query("SELECT qs FROM QuickShare qs WHERE qs.expiredAt <= :date")
    List<QuickShare> findAllExpired(@Param("date") Date date);
}
