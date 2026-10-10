package com.miniauthorizer.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ApiUserRepository extends JpaRepository<ApiUser, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ApiUser u set u.failedAttempts = u.failedAttempts + 1 where u.username = :username")
    int incrementFailedAttempts(@Param("username") String username);

    /**
     * Locks the user once the failed attempts reach the limit, and starts a new count for the
     * period after the lock.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ApiUser u
               set u.lockedUntil = :until, u.failedAttempts = 0
             where u.username = :username and u.failedAttempts >= :maxAttempts
            """)
    int lockWhenAttemptsReach(@Param("username") String username,
                              @Param("maxAttempts") int maxAttempts,
                              @Param("until") Instant until);

    // The condition keeps the common case, a successful login with no failures, from writing.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ApiUser u set u.failedAttempts = 0 where u.username = :username and u.failedAttempts > 0")
    int resetFailedAttempts(@Param("username") String username);
}
