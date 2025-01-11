package com.xiaotao.saltedfishcloud.config.security.service;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.Objects;
import java.util.Optional;

@Service("userDetailsService")
public class UserDetailsServiceImpl implements UserDetailsService {
    @Resource
    private UserService userService;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return Optional.ofNullable(userService.getUserByAccount(username))
                    .orElseThrow(() -> new UsernameNotFoundException(username));
        } catch (UserNoExistException e) {
            throw new UsernameNotFoundException(username);
        }
    }
}
