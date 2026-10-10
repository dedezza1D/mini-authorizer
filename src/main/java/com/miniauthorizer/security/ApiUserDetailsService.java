package com.miniauthorizer.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ApiUserDetailsService implements UserDetailsService {

    private final ApiUserRepository repository;
    private final Clock clock;

    public ApiUserDetailsService(ApiUserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return repository.findById(username)
                .map(user -> user.toUserDetails(clock.instant()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown API user"));
    }
}
