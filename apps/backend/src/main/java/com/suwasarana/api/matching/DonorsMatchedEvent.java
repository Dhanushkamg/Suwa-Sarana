package com.suwasarana.api.matching;

import java.util.List;

/**
 * Published inside the matching transaction; handled only AFTER it commits, so a rollback of the request
 * (or of the matches) never leaves donors alerted about something that does not exist.
 */
public record DonorsMatchedEvent(Long requestId, List<Long> matchIds) {}
