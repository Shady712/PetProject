package com.partycipate.controllers;

import com.partycipate.exception.PartycipateException;
import com.partycipate.services.EventService;
import com.partycipate.services.FriendRequestService;
import com.partycipate.services.InviteService;
import com.partycipate.services.UserService;
import com.partycipate.model.dtos.UserRegisterDto;
import com.partycipate.model.dtos.UserResponseDto;
import com.partycipate.model.dtos.UserUpdateDto;
import com.partycipate.model.entities.User;
import lombok.AllArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/user")
@CrossOrigin("https://partycipate.herokuapp.com")
public class UserController {
    private final UserService userService;
    private final EventService eventService;
    private final InviteService inviteService;
    private final ConversionService conversionService;
    private final FriendRequestService friendRequestService;

    @PostMapping("/register")
    public UserResponseDto register(@RequestBody @Valid UserRegisterDto userRegisterDto) {
        if (!userService.checkLoginVacancy(userRegisterDto.getLogin())) {
            throw new PartycipateException("Provided login is already in use");
        }
        if (!userService.checkEmailVacancy(userRegisterDto.getEmail())) {
            throw new PartycipateException("Provided email is already in use");
        }
        return conversionService.convert(
                userService.register(Objects.requireNonNull(conversionService.convert(userRegisterDto, User.class))),
                UserResponseDto.class
        );
    }

    @GetMapping("/findById")
    public UserResponseDto findById(@RequestParam Long id) {
        return conversionService.convert(
                userService.findById(id)
                        .orElseThrow(() -> new PartycipateException("User with provided id does not exist")),
                UserResponseDto.class
        );
    }

    @GetMapping("/findByLogin")
    public UserResponseDto findByLogin(@RequestParam String login) {
        return conversionService.convert(
                userService.findByLogin(login)
                        .orElseThrow(() -> new PartycipateException("User with provided login does not exist")),
                UserResponseDto.class
        );
    }

    @GetMapping("/isLoginVacant")
    public Boolean isLoginVacant(@RequestParam String login) {
        return userService.checkLoginVacancy(login);
    }

    @GetMapping("/isEmailVacant")
    public Boolean isEmailVacant(@RequestParam String email) {
        return userService.checkEmailVacancy(email);
    }

    @GetMapping("/enter")
    public UserResponseDto enterByJwt(@RequestParam String jwt) {
        return conversionService.convert(
                userService.findByJwt(jwt)
                        .orElseThrow(() -> new PartycipateException("User with provided id does not exist")),
                UserResponseDto.class
        );
    }

    @GetMapping("/createJwt")
    public String createJwt(@RequestParam String login, @RequestParam String password) {
        var user = userService.findByLogin(login)
                .orElseThrow(() -> new PartycipateException("Invalid login or password"));
        if (!userService.checkPassword(user, password)) {
            throw new PartycipateException("Invalid login or password");
        }
        if (!user.getEmailVerified()) {
            throw new PartycipateException("You must verify your email address first");
        }
        return userService.createJwtToken(user);
    }

    @GetMapping("/findAllByLoginPrefix")
    public List<UserResponseDto> findAllByLoginPrefix(@RequestParam String prefix) {
        return userService.findAllByLoginPrefix(prefix)
                .stream()
                .map(user -> conversionService.convert(user, UserResponseDto.class))
                .toList();
    }

    @Transactional
    @GetMapping("/findAllFriends")
    public List<UserResponseDto> findAllFriends(@RequestParam String login) {
        if (userService.findByLogin(login).isEmpty()) {
            throw new PartycipateException("User with provided login does not exist");
        }
        return userService.findAllFriends(login)
                .stream()
                .map(user -> conversionService.convert(user, UserResponseDto.class))
                .toList();
    }

    @PutMapping("/update")
    public UserResponseDto update(@RequestBody @Valid UserUpdateDto userUpdateDto) {
        if (userService.findByJwt(userUpdateDto.getJwt()).isEmpty()) {
            throw new PartycipateException("You are not authorized");
        }
        return conversionService.convert(
                userService.update(Objects.requireNonNull(conversionService.convert(userUpdateDto, User.class))),
                UserResponseDto.class
        );
    }

    // Get потому что браузер отправляет только GET запросы
    @GetMapping("/verifyEmail")
    public String verifyEmail(@RequestParam String login, @RequestParam String passwordHash) {
        var user = userService.findByLoginAndPasswordHash(login, passwordHash)
                .orElseThrow(() -> new PartycipateException("Authorization failed"));
        if (user.getEmailVerified()) {
            throw new PartycipateException("Your email is already verified");
        }
        userService.verifyEmail(user);
        return "Your email is verified. You may close this page.";
    }

    @GetMapping("/requestPasswordChange")
    public void requestPasswordChange(@RequestParam String loginOrEmail) {
        var user = userService.findByLogin(loginOrEmail).orElseGet(() -> userService.findByEmail(loginOrEmail)
                .orElseThrow(() -> new PartycipateException("Invalid login or email")));
        if (!user.getEmailVerified()) {
            throw new PartycipateException("You must verify your email address first");
        }
        userService.requestPasswordChange(user);
    }

    @PutMapping("/changePassword")
    public UserResponseDto changePassword(
            @RequestParam String login,
            @RequestParam String passwordHash,
            @RequestParam String newPassword
    ) {
        return conversionService.convert(
                userService.changePassword(
                        userService.findByLoginAndPasswordHash(login, passwordHash)
                                .orElseThrow(() -> new PartycipateException("Authorization failed")),
                        newPassword
                ),
                UserResponseDto.class
        );
    }

    @DeleteMapping("/delete")
    public void delete(@RequestParam String jwt) {
        var user = userService
                .findByJwt(jwt).orElseThrow(() -> new PartycipateException("You are not authorized"));
        if (!eventService.findAllByCreator(user).isEmpty()) {
            throw new PartycipateException("You need to finish all the events first");
        }
        inviteService.findAllIncoming(user)
                .forEach(inviteService::deleteInvite);
        friendRequestService.findAllOutgoing(user)
                .forEach(friendRequestService::deleteRequest);
        friendRequestService.findAllIncoming(user)
                .forEach(friendRequestService::deleteRequest);
        userService.delete(user);
    }
}
