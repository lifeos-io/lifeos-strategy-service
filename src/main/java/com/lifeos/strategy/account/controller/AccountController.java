package com.lifeos.strategy.account.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.account.service.AccountDeletionService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code DELETE /api/strategy/me}: KVKK deletion of the user's data (reading and editing is in ProfileController). */
@RestController
@RequestMapping("/api/strategy/me")
public class AccountController {

    private final AccountDeletionService accountDeletionService;

    public AccountController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    /** Deletes everything at once; the identity account follows asynchronously. Always 204, also when repeated. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@CurrentUser UUID userId) {
        accountDeletionService.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
