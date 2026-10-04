package com.credchain.modules.user.infrastructure;

import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<User> findAllByInstitutionId(UUID institutionId);
}