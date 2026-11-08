package com.lifeos.strategy.profile.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.profile.dto.ProfileResponse;
import com.lifeos.strategy.profile.dto.UpdateProfileRequest;
import com.lifeos.strategy.profile.service.ProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/me}: the signed-in user's profile. */
@RestController
@RequestMapping("/api/strategy/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** Creates the profile with defaults on the user's first call. */
    @GetMapping
    public ProfileResponse get(@CurrentUser UUID userId) {
        return profileService.getOrCreate(userId);
    }

    @PatchMapping
    public ProfileResponse update(@CurrentUser UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(userId, request);
    }
}
