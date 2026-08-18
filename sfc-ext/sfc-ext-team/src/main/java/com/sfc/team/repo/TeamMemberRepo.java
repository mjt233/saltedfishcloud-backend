package com.sfc.team.repo;

import com.sfc.team.model.TeamMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 团队成员实体仓库
 */
public interface TeamMemberRepo extends JpaRepository<TeamMember, Long> {

    /**
     * 按团队与用户查询成员（不限状态）
     *
     * @param teamId 团队 id
     * @param uid    用户 id
     * @return 成员
     */
    Optional<TeamMember> findByTeamIdAndUid(Long teamId, Long uid);

    /**
     * 按团队与用户查询正常成员
     *
     * @param teamId 团队 id
     * @param uid    用户 id
     * @param status 成员状态
     * @return 成员
     */
    Optional<TeamMember> findByTeamIdAndUidAndStatus(Long teamId, Long uid, Integer status);

    /**
     * 查询某用户加入的全部正常成员记录
     *
     * @param uid    用户 id
     * @param status 成员状态
     * @return 成员记录列表
     */
    List<TeamMember> findByUidAndStatus(Long uid, Integer status);

    /**
     * 分页查询团队正常成员
     *
     * @param teamId 团队 id
     * @param status 成员状态
     * @param pageable 分页参数
     * @return 分页成员
     */
    Page<TeamMember> findByTeamIdAndStatus(Long teamId, Integer status, Pageable pageable);

    /**
     * 统计团队成员数量
     *
     * @param teamId 团队 id
     * @param status 成员状态
     * @return 成员数量
     */
    long countByTeamIdAndStatus(Long teamId, Integer status);

    /**
     * 按团队 id 集合批量查询正常成员（用于内存分组统计各团队人数）
     *
     * @param teamIds 团队 id 集合
     * @param status  成员状态
     * @return 成员记录列表
     */
    List<TeamMember> findByTeamIdInAndStatus(Collection<Long> teamIds, Integer status);
}
