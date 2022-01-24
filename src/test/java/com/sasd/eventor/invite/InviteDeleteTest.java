package com.sasd.eventor.invite;

import com.sasd.eventor.exception.EventorException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.sasd.eventor.utils.InviteUtils.validInviteCreateDto;
import static com.sasd.eventor.utils.UserUtils.validUserRegisterDto;

public class InviteDeleteTest extends InviteTest {

    @Test
    public void ensureBadRequestForFindingDeletedInvite() {
        var jwt = registerValidUserAndGetJwt();
        var createdEvent = createEvent(jwt);
        var inviteCreateDto = validInviteCreateDto(
                jwtService.decodeJwtToId(jwt), createdEvent.getId());
        var createdInvite = inviteController.create(inviteCreateDto);
        inviteController.deleteById(createdInvite.getId(), getJwt());
        Assertions.assertThrows(EventorException.class, () -> eventController.findById(createdInvite.getId()));
    }

    @Test
    public void ensureBadRequestForDeletingUncreatedInvite() {
        var jwt = registerValidUserAndGetJwt();
        var createdEvent = createEvent(jwt);
        var inviteCreateDto = validInviteCreateDto(
                jwtService.decodeJwtToId(jwt), createdEvent.getId());
        var createdInvite = inviteController.create(inviteCreateDto);
        Assertions.assertThrows(EventorException.class,
                () -> inviteController.deleteById(createdInvite.getId() + 100, getJwt()));
    }

    private String getJwt() {
        var userRegisterDto = validUserRegisterDto();
        userController.register(userRegisterDto);
        return userController.createJwt(userRegisterDto.getLogin(), userRegisterDto.getPassword());
    }
}