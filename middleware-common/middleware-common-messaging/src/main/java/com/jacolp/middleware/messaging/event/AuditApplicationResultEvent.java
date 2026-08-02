package com.jacolp.middleware.messaging.event;

public record AuditApplicationResultEvent(String commandId,
        AuditApplicationRequestedEvent.TargetType targetType, long targetId,
        Outcome outcome, Long auditApplicationId, String reasonCode) {
    public enum Outcome { ACCEPTED, REJECTED, CANCELLED, CANCEL_REJECTED }
    public AuditApplicationResultEvent {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        if (targetType == null || outcome == null) throw new IllegalArgumentException("type and outcome are required");
        if (targetId <= 0) throw new IllegalArgumentException("targetId must be positive");
    }
}
