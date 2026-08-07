package com.jacolp.middleware.messaging.constant;

/** Central RabbitMQ names; business code must not duplicate literal topology names. */
public final class EventTopology {
    public static final String EXCHANGE = "middleware.domain.events";

    public static final String NOTE_QUEUE = "middleware.note.events";
    public static final String MEDIA_QUEUE = "middleware.media.events";
    public static final String SYSTEM_QUEUE = "middleware.system.events";
    public static final String EMAIL_QUEUE = "middleware.system.email";
    public static final String MEDIA_DELETE_QUEUE = "middleware.media.resource-delete";
    public static final String AUDIT_PROJECTION_QUEUE = "middleware.audit.projections";

    private EventTopology() {
    }

    public static String retryQueue(String queue) {
        return queue + ".retry";
    }

    public static String deadLetterQueue(String queue) {
        return queue + ".dlq";
    }
}
