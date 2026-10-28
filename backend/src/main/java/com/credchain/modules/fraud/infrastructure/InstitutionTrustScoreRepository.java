package com.credchain.modules.fraud.infrastructure;

import com.credchain.modules.fraud.domain.InstitutionTrustScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InstitutionTrustScoreRepository extends JpaRepository<InstitutionTrustScore, UUID> {
}
