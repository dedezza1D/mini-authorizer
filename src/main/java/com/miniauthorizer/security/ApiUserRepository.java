package com.miniauthorizer.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiUserRepository extends JpaRepository<ApiUser, String> {
}
