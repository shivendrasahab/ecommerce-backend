package com.shivendra.ecommerce.repositories;

import com.shivendra.ecommerce.models.Session;
import com.shivendra.ecommerce.models.SessionStatus;
import com.shivendra.ecommerce.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByToken(String token);

    List<Session> findByUserAndStatus(User user, SessionStatus status);
}
