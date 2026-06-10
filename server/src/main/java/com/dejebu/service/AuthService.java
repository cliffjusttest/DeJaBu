package com.dejebu.service;

import com.dejebu.dto.AuthResponse;
import com.dejebu.dto.CreateCharacterRequest;
import com.dejebu.dto.LoginRequest;
import com.dejebu.dto.RegisterRequest;
import com.dejebu.game.Element;
import com.dejebu.game.CharacterStats;
import com.dejebu.entity.AuthToken;
import com.dejebu.entity.User;
import com.dejebu.repository.AuthTokenRepository;
import com.dejebu.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final long tokenExpireDays;

    public AuthService(UserRepository userRepository,
                       AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${dejebu.auth.token-expire-days:7}") long tokenExpireDays) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenExpireDays = tokenExpireDays;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("帳號已被使用");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.username());
        userRepository.save(user);

        return issueToken(user, "註冊成功，請創建角色");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("帳號或密碼錯誤"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("帳號或密碼錯誤");
        }

        String message = user.isHasCharacter() ? "登入成功" : "登入成功，請創建角色";
        return issueToken(user, message);
    }

    @Transactional
    public AuthResponse createCharacter(CreateCharacterRequest request) {
        User user = validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));

        if (user.isHasCharacter()) {
            throw new IllegalArgumentException("角色已存在");
        }

        Element element = request.element();
        if (element == null || !element.isSelectable()) {
            throw new IllegalArgumentException("請選擇有效的元素屬性");
        }

        user.setDisplayName(request.displayName().trim());
        user.setElement(element);
        user.setAppearance(request.appearance());

        CharacterStats stats = request.stats();
        stats.validateCreation();
        user.applyStats(stats);
        user.setPlayerCurrentHp(stats.maxHp());

        user.setHasCharacter(true);
        userRepository.save(user);

        return issueToken(user, "角色創建成功，歡迎來到 DeJaBu");
    }

    @Transactional(readOnly = true)
    public Optional<User> validateToken(String rawToken) {
        UUID tokenId;
        try {
            tokenId = UUID.fromString(rawToken);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        return authTokenRepository.findByTokenAndExpiresAtAfter(tokenId, Instant.now())
                .map(authToken -> {
                    User user = authToken.getUser();
                    user.getDisplayName();
                    user.getPlayerX();
                    user.getPlayerY();
                    user.isHasCharacter();
                    return user;
                });
    }

    @Transactional(readOnly = true)
    public Optional<Element> findUserElement(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isHasCharacter)
                .map(User::getElement);
    }

    @Transactional(readOnly = true)
    public Optional<CharacterStats> findUserStats(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isHasCharacter)
                .map(CharacterStats::fromUser);
    }

    @Transactional(readOnly = true)
    public Optional<Integer> findUserLevel(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isHasCharacter)
                .map(User::getLevel);
    }

    @Transactional
    public void updatePlayerPosition(Long userId, String mapId, int x, int y) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setPlayerMapId(mapId);
            user.setPlayerX(x);
            user.setPlayerY(y);
            userRepository.save(user);
        });
    }

    @Transactional
    public void syncPlayerHp(Long userId, int hp) {
        userRepository.findById(userId).ifPresent(user -> {
            int maxHp = user.resolveMaxHp();
            user.setPlayerCurrentHp(Math.max(0, Math.min(hp, maxHp)));
            userRepository.save(user);
        });
    }

    @Transactional
    protected AuthResponse issueToken(User user, String message) {
        authTokenRepository.deleteExpired(Instant.now());

        AuthToken authToken = new AuthToken();
        authToken.setToken(UUID.randomUUID());
        authToken.setUser(user);
        authToken.setExpiresAt(Instant.now().plus(tokenExpireDays, ChronoUnit.DAYS));
        authTokenRepository.save(authToken);

        return new AuthResponse(
                authToken.getToken().toString(),
                user.getId(),
                user.getDisplayName(),
                user.getPlayerX(),
                user.getPlayerY(),
                user.getPlayerMapId(),
                user.isHasCharacter(),
                user.getElement(),
                user.getAppearance(),
                user.isHasCharacter() ? CharacterStats.fromUser(user) : null,
                message
        );
    }
}
