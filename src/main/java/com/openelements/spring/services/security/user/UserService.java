package com.openelements.spring.services.security.user;

import com.openelements.spring.services.security.AuthService;
import com.openelements.spring.services.security.UserInformation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class UserService {

    private static final List<String> ALLOWED_AVATAR_TYPES = List.of("image/jpeg", "image/png");
    private static final int MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthService authService;

    public UserService(final UserRepository userRepository,
                       final ApplicationEventPublisher eventPublisher,
                       final AuthService authService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    private UserEntity getCurrentUserEntity() {
        final UserInformation userInformation = authService.getUserInformation();
        return userRepository.findBySub(userInformation.id())
                .map(existing -> {
                    boolean changed = false;
                    if (!Objects.equals(userInformation.name(), existing.getName())) {
                        existing.setName(userInformation.name());
                        changed = true;
                    }
                    if (!Objects.equals(userInformation.email(), existing.getEmail())) {
                        existing.setEmail(userInformation.email());
                        changed = true;
                    }
                    return changed ? userRepository.saveAndFlush(existing) : existing;
                })
                .orElseGet(() -> {
                    final UserEntity entity = new UserEntity();
                    entity.setSub(userInformation.id());
                    entity.setName(userInformation.name());
                    entity.setEmail(userInformation.email());
                    final UserEntity saved = userRepository.saveAndFlush(entity);
                    return saved;
                });
    }

    public UserDto getCurrentUser() {
        return UserDto.fromEntity(getCurrentUserEntity());
    }

    public UserDto uploadAvatarForCurrentUser(final byte[] data, final String contentType) {
        if (data == null || data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image data provided");
        }
        if (data.length > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File too large (max 2MB)");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid content type: " + contentType + ". Allowed: " + ALLOWED_AVATAR_TYPES);
        }
        final UserEntity user = getCurrentUserEntity();
        user.setAvatar(data);
        user.setAvatarContentType(contentType);
        return UserDto.fromEntity(userRepository.saveAndFlush(user));
    }

    public ImageData getAvatarOfCurrentUser() {
        final UserEntity user = getCurrentUserEntity();
        if (user.getAvatar() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No avatar set");
        }
        return new ImageData(user.getAvatar(), user.getAvatarContentType());
    }

    public void deleteAvatarOfCurrentUser() {
        final UserEntity user = getCurrentUserEntity();
        user.setAvatar(null);
        user.setAvatarContentType(null);
        userRepository.saveAndFlush(user);
    }
}
