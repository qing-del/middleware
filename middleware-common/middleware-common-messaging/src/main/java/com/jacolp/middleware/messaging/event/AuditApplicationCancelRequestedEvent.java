package com.jacolp.middleware.messaging.event;

public record AuditApplicationCancelRequestedEvent(String commandId,
        AuditApplicationRequestedEvent.TargetType targetType, long targetId, long actorUserId) {
    public AuditApplicationCancelRequestedEvent {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        if (targetType == null) throw new IllegalArgumentException("targetType is required");
        if (targetId <= 0 || actorUserId <= 0) throw new IllegalArgumentException("ids must be positive");
    }
}
