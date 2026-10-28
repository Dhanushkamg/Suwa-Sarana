package com.suwasarana.api.circle;

/**
 * Tells the requester that a friend or relative has pledged through their private circle.
 *
 * <p>Implementations (for example an in-app notification inbox) are discovered as Spring beans; the circle service
 * calls all of them. The service only tells the volunteer "the requester has been notified" when at least one
 * implementation reports that a notification was really created.
 */
public interface PledgeNotifier {

    /**
     * Carries no phone number or other contact data on purpose: the requester reads those in the circle panel.
     *
     * @param requestId       the blood request
     * @param requesterUserId id of the user who owns the request
     * @param volunteerName   display name the volunteer entered
     * @param bloodType       blood type the volunteer entered
     * @param pledgeCount     total number of pledges the circle has now
     * @return {@code true} only if a notification for the requester was actually created / queued
     */
    boolean pledgeReceived(long requestId, long requesterUserId, String volunteerName, String bloodType, int pledgeCount);
}
