package com.system.SchoolManagementSystem.auth.repository;

import com.system.SchoolManagementSystem.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.tenantId = :tenantId")
    Optional<User> findByUsernameAndTenantId(@Param("username") String username,
                                             @Param("tenantId") String tenantId);

    boolean existsByUsernameAndTenantId(String username, String tenantId);

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId")
    java.util.List<User> findByTenantId(@Param("tenantId") String tenantId);
}