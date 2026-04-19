package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface UserRepo extends JpaRepository<UserInfo, Long> {

	UserInfo findByEmail(String email);

	UserInfo findByUser(String user);

	List<UserInfo> findAllByIdIn(Collection<Long> ids);

	@Transactional
	@Modifying
	@Query(value = "UPDATE user SET email = :email WHERE id = :id")
	int updateEmail(@Param("id") Long id, @Param("email") String email);

	@Transactional
	@Modifying
	@Query(value = "UPDATE user SET type = :type WHERE id = :uid")
	int grant(@Param("uid") Long uid, @Param("type") Integer type);

	@Transactional
	@Modifying
	@Query(value = "UPDATE user SET pwd = :pwd WHERE id = :uid")
	int modifyPassword(@Param("uid") Long uid, @Param("pwd") String encodedPassword);

	@Transactional
	@Modifying
	@Query(value = "UPDATE user SET lastLogin = :loginTime WHERE id = :uid")
	int updateLoginDate(@Param("uid") Long uid, @Param("loginTime") Long loginTime);

	/**
	 * 按用户名或邮箱模糊搜索用户。
	 *
	 * @param keyword  搜索关键词
	 * @param pageable 分页参数
	 * @return 用户分页结果
	 */
	@Query("""
		SELECT u
		FROM user u
		WHERE u.user LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
		OR u.email LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
	""")
	Page<UserInfo> searchUsers(@Param("keyword") String keyword, Pageable pageable);

	static User toUser(UserInfo userInfo) {
		if (userInfo == null) {
			return null;
		}
		User user = new User();
		BeanUtils.copyProperties(userInfo, user);
		return user;
	}

	static User toBaseUser(UserInfo userInfo) {
		if (userInfo == null) {
			return null;
		}
		User user = new User();
		user.setId(userInfo.getId());
		user.setUser(userInfo.getUser());
		return user;
	}

	default List<User> findBaseInfoByIds(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Collections.emptyList();
		}
		return findAllByIdIn(ids).stream().map(UserRepo::toBaseUser).toList();
	}

	default User getByEmail(String email) {
		return toUser(findByEmail(email));
	}

	default User getUserById(Long id) {
		return findById(id).map(UserRepo::toUser).orElse(null);
	}

	default User getUserByUser(String user) {
		return toUser(findByUser(user));
	}

	default List<User> getUserList() {
		return findAll().stream().map(UserRepo::toUser).toList();
	}
}
