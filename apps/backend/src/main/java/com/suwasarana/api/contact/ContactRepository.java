package com.suwasarana.api.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactRepository extends JpaRepository<ContactMessage, Long> {

    Page<ContactMessage> findByStatus(ContactStatus status, Pageable pageable);
}
