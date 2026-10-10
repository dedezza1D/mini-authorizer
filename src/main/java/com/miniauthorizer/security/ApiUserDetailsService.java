package com.miniauthorizer.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiUserDetailsService implements UserDetailsService {

    private final ApiUserRepository repository;

    public ApiUserDetailsService(ApiUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return repository.findById(username)
                .map(ApiUser::toUserDetails)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown API user"));
    }
}
