package com.openelements.spring.services.keys;

import com.openelements.spring.services.tenant.UserPrincipalService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KeyService {

    private final KeyDataService service;

    private final UserPrincipalService userPrincipalService;

    public KeyService(KeyDataService service, UserPrincipalService userPrincipalService) {
        this.service = service;
        this.userPrincipalService = userPrincipalService;
    }

    public KeyData createKeyForCurrentUser() {
        final String userName = userPrincipalService.getPrincipal().getName();
        final String key = UUID.randomUUID().toString();
        final KeyData keyData = new KeyData(null, userName, key);
        final KeyData savedData = service.save(keyData);
        return new KeyData(savedData.id(), savedData.principal(), key);
    }

    public boolean isKeyAvailableForCurrentUser() {
        final String userName = userPrincipalService.getPrincipal().getName();
        return service.findByPrincipal(userName).isPresent();
    }

    public boolean validateForCurrentUser(WithApiKey data) {
        return service.isKeyValid(data);
    }
}
