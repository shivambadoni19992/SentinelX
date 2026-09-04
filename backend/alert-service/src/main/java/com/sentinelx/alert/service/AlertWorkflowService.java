package com.sentinelx.alert.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.sentinelx.alert.domain.AlertAction;
import com.sentinelx.alert.domain.AlertStatus;
import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;

/**
 * Alert workflow: applies response {@link AlertAction}s (driving real state
 * changes through {@link ResponseActionExecutor}) and enforces the
 * {@link AlertStatus} lifecycle. Every mutation persists on the alert and
 * emits an audit event via {@link AuditEventPublisher}.
 */
@Service
public class AlertWorkflowService {

    private final SecurityAlertRepository alerts;
    private final ResponseActionExecutor executor;
    private final AuditEventPublisher audit;

    public AlertWorkflowService(SecurityAlertRepository alerts,
                                ResponseActionExecutor executor,
                                AuditEventPublisher audit) {
        this.alerts = alerts;
        this.executor = executor;
        this.audit = audit;
    }

    /** Applies a response action and returns the updated alert. */
    public SecurityAlert applyAction(java.util.UUID alertId, AlertAction action, String actor) {
        SecurityAlert alert = alerts.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("alert not found: " + alertId));
        String who = actor == null || actor.isBlank() ? "system" : actor;

        Map<String, Object> detail = executor.apply(alert, action);
        alert.setAction(action.name());
        alert.setActor(who);
        alert.setActionDetail(detail);
        SecurityAlert saved = alerts.save(alert);

        audit.alertActionApplied(alertId, action.name(), who, detail);
        return saved;
    }

    /** Moves the alert through its status lifecycle and audits the change. */
    public SecurityAlert changeStatus(java.util.UUID alertId, AlertStatus newStatus, String actor) {
        SecurityAlert alert = alerts.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("alert not found: " + alertId));
        String who = actor == null || actor.isBlank() ? "system" : actor;
        String old = alert.getStatus();

        alert.setStatus(newStatus.name());
        alert.setActor(who);
        SecurityAlert saved = alerts.save(alert);

        audit.alertStatusChanged(alertId, old, newStatus.name(), who);
        return saved;
    }
}