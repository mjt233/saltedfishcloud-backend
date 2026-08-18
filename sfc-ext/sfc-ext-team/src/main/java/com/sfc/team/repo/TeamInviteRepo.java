package com.sfc.team.repo;

import com.sfc.team.model.TeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 团队邀请实体仓库
 */
public interface TeamInviteRepo extends JpaRepository<TeamInvite, Long> {

    /**
     * 按令牌查询邀请
     *
     * @param token 邀请令牌
     * @return 邀请
     */
    Optional<TeamInvite> findByToken(String token);

    /**
     * 按团队查询某状态的邀请列表
     *
     * @param teamId 团队 id
     * @param status 邀请状态
     * @return 邀请列表
     */
    List<TeamInvite> findByTeamIdAndStatus(Long teamId, Integer status);

    /**
     * 按 id 与团队查询邀请
     *
     * @param id     邀请 id
     * @param teamId 团队 id
     * @return 邀请
     */
    Optional<TeamInvite> findByIdAndTeamId(Long id, Long teamId);
}
