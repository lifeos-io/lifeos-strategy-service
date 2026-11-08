package com.lifeos.strategy.action.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.action.dto.ActionResponse;
import com.lifeos.strategy.action.dto.ChangeActionStatusRequest;
import com.lifeos.strategy.action.dto.CreateActionRequest;
import com.lifeos.strategy.action.dto.UpdateActionRequest;
import com.lifeos.strategy.action.service.ActionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/weekly-plans/{isoWeek}/actions}: the work of a week. */
@RestController
@RequestMapping("/api/strategy/weekly-plans/{isoWeek}/actions")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping
    public ResponseEntity<ActionResponse> create(@CurrentUser UUID userId, @PathVariable String isoWeek,
            @Valid @RequestBody CreateActionRequest request) {
        ActionResponse action = actionService.create(userId, isoWeek, request);
        URI location = URI.create("/api/strategy/weekly-plans/" + isoWeek + "/actions/" + action.id());
        return ResponseEntity.created(location).body(action);
    }

    @GetMapping
    public List<ActionResponse> list(@CurrentUser UUID userId, @PathVariable String isoWeek) {
        return actionService.list(userId, isoWeek);
    }

    @GetMapping("/{actionId}")
    public ActionResponse get(@CurrentUser UUID userId, @PathVariable String isoWeek, @PathVariable UUID actionId) {
        return actionService.get(userId, isoWeek, actionId);
    }

    @PatchMapping("/{actionId}")
    public ActionResponse update(@CurrentUser UUID userId, @PathVariable String isoWeek,
            @PathVariable UUID actionId, @Valid @RequestBody UpdateActionRequest request) {
        return actionService.update(userId, isoWeek, actionId, request);
    }

    /** PLANNED / DONE / DEFERRED / DROPPED, only in the active week. */
    @PutMapping("/{actionId}/status")
    public ActionResponse changeStatus(@CurrentUser UUID userId, @PathVariable String isoWeek,
            @PathVariable UUID actionId, @Valid @RequestBody ChangeActionStatusRequest request) {
        return actionService.changeStatus(userId, isoWeek, actionId, request);
    }

    @DeleteMapping("/{actionId}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable String isoWeek,
            @PathVariable UUID actionId) {
        actionService.delete(userId, isoWeek, actionId);
        return ResponseEntity.noContent().build();
    }
}
