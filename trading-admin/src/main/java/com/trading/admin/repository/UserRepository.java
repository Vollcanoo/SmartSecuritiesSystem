// trading-admin/src/main/java/com/trading/admin/repository/UserRepository.java
package com.trading.admin.repository;

import com.trading.admin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 根据uid查询
    Optional<User> findByUid(Long uid);

    // 根据股东号查询
    Optional<User> findByShareholderId(String shareholderId);

    // 根据username查询
    Optional<User> findByUsername(String username);
}
