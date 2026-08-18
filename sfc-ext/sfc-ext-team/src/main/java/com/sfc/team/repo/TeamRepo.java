package com.sfc.team.repo;

import com.sfc.team.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 团队实体仓库
 */
public interface TeamRepo extends JpaRepository<Team, Long> {

    /**
     * 按 id 与状态查询团队
     *
     * @param id     团队 id
     * @param status 团队状态
     * @return 团队
     */
    Optional<Team> findByIdAndStatus(Long id, Integer status);

    /**
     * 统计某用户创建的正常团队数量
     *
     * @param ownerUid 所有者用户 id
     * @param status   团队状态
     * @return 团队数量
     */
    long countByOwnerUidAndStatus(Long ownerUid, Integer status);
}
