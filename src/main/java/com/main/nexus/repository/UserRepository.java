package com.main.nexus.repository;

import com.main.nexus.model.User;
import com.main.nexus.model.enums.UserType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByLinkedinId(String linkedinId);
    List<User> findByType(UserType type);
}