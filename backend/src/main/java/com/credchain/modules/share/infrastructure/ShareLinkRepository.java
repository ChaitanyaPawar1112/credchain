package com.credchain.modules.share.infrastructure;

import com.credchain.modules.share.domain.ShareLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByToken(String token);

    /** Locked, so two people opening the link at the same moment are both counted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM ShareLink l WHERE l.token = :token")
    Optional<ShareLink> lockByToken(@Param("token") String token);

    Optional<ShareLink> findByIdAndStudentId(UUID id, UUID studentId);

    Page<ShareLink> findAllByStudentId(UUID studentId, Pageable pageable);

    @Query("SELECT COUNT(l) FROM ShareLink l WHERE l.studentId = :studentId AND l.revokedAt IS NULL AND l.expiresAt > :now")
    long countActive(@Param("studentId") UUID studentId, @Param("now") Instant now);
}
