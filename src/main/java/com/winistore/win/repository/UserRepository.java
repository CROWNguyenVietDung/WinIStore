package com.winistore.win.repository;

import com.winistore.win.model.entity.User;
import com.winistore.win.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);

    List<User> findAllByRole(Role role);
}
