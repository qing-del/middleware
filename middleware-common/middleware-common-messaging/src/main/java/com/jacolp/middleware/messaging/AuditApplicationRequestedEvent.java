package com.jacolp.middleware.messaging;

public record AuditApplicationRequestedEvent(String commandId, TargetType targetType, long targetId,
                                             long applicantUserId, String applyReason,
                                             String targetName, String targetUrl) {
    public enum TargetType { NOTE, TAG, IMAGE }
    public AuditApplicationRequestedEvent {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        if (targetType == null) throw new IllegalArgumentException("targetType is required");
        if (targetId <= 0 || applicantUserId <= 0) throw new IllegalArgumentException("ids must be positive");
        if (targetName == null || targetName.isBlank()) throw new IllegalArgumentException("targetName is required");
    }
}
