package com.jacolp.middleware.messaging.constant;

/** Public business event names. Values are wire contracts and must remain stable. */
public final class EventTypes {
    public static final String AUDIT_REVIEWED = "audit.reviewed";
    public static final String STORAGE_RELEASED = "storage.released";
    public static final String MEDIA_RESOURCE_DELETE_REQUESTED = "media.resource.delete-requested";
    public static final String EMAIL_SEND_REQUESTED = "email.send-requested";
    public static final String USER_PROFILE_CHANGED = "user.profile-changed";

    private EventTypes() {
    }
}
