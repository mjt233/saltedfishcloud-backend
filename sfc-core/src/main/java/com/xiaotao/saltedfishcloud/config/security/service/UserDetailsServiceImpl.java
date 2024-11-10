package com.xiaotao.saltedfishcloud.config.security.service;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service("userDetailsService")
public class UserDetailsServiceImpl implements UserDetailsService {
    @Resource
    private UserService userService;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return userService.getUserByAccount(username);
        } catch (UserNoExistException e) {
            throw new UsernameNotFoundException(e.getMessage());
        }
    }
}
