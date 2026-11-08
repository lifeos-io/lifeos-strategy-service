package com.lifeos.strategy.action.service;

import com.lifeos.strategy.action.dto.ActionResponse;
import com.lifeos.strategy.action.dto.ChangeActionStatusRequest;
import com.lifeos.strategy.action.dto.CreateActionRequest;
import com.lifeos.strategy.action.dto.UpdateActionRequest;
import java.util.List;
import java.util.UUID;

/**
 * The actions of a week. Content can change while the week is a draft or active; statuses change only in the
 * active week, and each change (or an action added to the active week) publishes {@code ActionStatusChanged}.
 * The estimates of the actions that are not dropped never exceed the week's capacity.
 */
public interface ActionService {

    ActionResponse create(UUID userId, String isoWeek, CreateActionRequest request);

    /** In the order the user arranged them. */
    List<ActionResponse> list(UUID userId, String isoWeek);

    ActionResponse get(UUID userId, String isoWeek, UUID actionId);

    ActionResponse update(UUID userId, String isoWeek, UUID actionId, UpdateActionRequest request);

    ActionResponse changeStatus(UUID userId, String isoWeek, UUID actionId, ChangeActionStatusRequest request);

    /** Only while the week is a draft; in the active week an action is dropped instead. */
    void delete(UUID userId, String isoWeek, UUID actionId);
}
