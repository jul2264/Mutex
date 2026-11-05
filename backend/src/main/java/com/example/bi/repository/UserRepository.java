package com.example.bi.repository;

import com.example.bi.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailNormalized(String emailNormalized);
    boolean existsByEmailNormalized(String emailNormalized);
}

