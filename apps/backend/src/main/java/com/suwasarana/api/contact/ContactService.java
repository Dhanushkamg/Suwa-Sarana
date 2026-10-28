package com.suwasarana.api.contact;

import com.suwasarana.api.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);
    static final int MAX_PAGE_SIZE = 100;

    private final ContactRepository repository;

    @Autowired
    public ContactService(ContactRepository repository) {
        this.repository = repository;
    }

    /** Stores a public contact message. Returns false (and stores nothing) when the honeypot field was filled. */
    @Transactional
    public boolean submit(ContactRequestDto dto) {
        if (dto.getWebsite() != null && !dto.getWebsite().isBlank()) {
            log.debug("Contact form honeypot triggered; message dropped");
            return false;
        }
        ContactMessage msg = new ContactMessage();
        msg.setName(clean(dto.getName(), false));
        msg.setEmail(clean(dto.getEmail(), false));
        msg.setSubject(clean(dto.getSubject(), false));
        msg.setMessage(clean(dto.getMessage(), true));
        msg.setStatus(ContactStatus.NEW);
        repository.save(msg);
        return true;
    }

    @Transactional(readOnly = true)
    public ContactPage list(ContactStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<ContactMessage> result = status == null ? repository.findAll(pr) : repository.findByStatus(status, pr);
        return new ContactPage(result.getContent().stream().map(ContactMessageDto::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ContactMessageDto setStatus(Long id, ContactStatus status, String adminEmail) {
        ContactMessage m = repository.findById(id).orElseThrow(() -> ApiException.notFound("Contact message not found"));
        if (status == ContactStatus.HANDLED) {
            if (m.getStatus() != ContactStatus.HANDLED) {
                m.setHandledAt(ZonedDateTime.now());
                m.setHandledBy(adminEmail);
            }
        } else {
            m.setHandledAt(null);
            m.setHandledBy(null);
        }
        m.setStatus(status);
        return ContactMessageDto.from(repository.save(m));
    }

    /** Trims, drops control characters (a NUL byte makes PostgreSQL reject the row) and, for single-line fields, newlines. */
    static String clean(String value, boolean keepLineBreaks) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(keepLineBreaks ? c : ' ');
            } else if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
