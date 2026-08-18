package com.sfc.team.repo;

import com.sfc.team.model.TeamAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 团队审计日志仓库
 */
public interface TeamAuditLogRepo extends JpaRepository<TeamAuditLog, Long> {
}
