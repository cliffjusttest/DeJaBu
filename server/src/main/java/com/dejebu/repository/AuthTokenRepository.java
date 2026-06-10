package com.dejebu.repository;

import com.dejebu.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    @Query("SELECT t FROM AuthToken t JOIN FETCH t.user WHERE t.token = :token AND t.expiresAt > :now")
    Optional<AuthToken> findByTokenAndExpiresAtAfter(@Param("token") UUID token, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :now")
    int deleteExpired(Instant now);
}
