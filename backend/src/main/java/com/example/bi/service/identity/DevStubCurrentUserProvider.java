package com.example.bi.service.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile({"dev", "test"})
public class DevStubCurrentUserProvider implements CurrentUserProvider {

    public static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public UUID getCurrentUserId() {
        return DEV_USER_ID;
    }
}

