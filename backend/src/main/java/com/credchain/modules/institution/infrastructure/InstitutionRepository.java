package com.credchain.modules.institution.infrastructure;

import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.domain.InstitutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    boolean existsByCode(String code);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByWalletAddress(String walletAddress);

    boolean existsByWalletAddressAndIdNot(String walletAddress, UUID id);

    Optional<Institution> findByCode(String code);

    Page<Institution> findAllByStatus(InstitutionStatus status, Pageable pageable);
}