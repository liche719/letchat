package com.letchat.controller;

import com.letchat.entity.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AGlobalExceptionHandlerControllerTest {

    private final AGlobalExceptionHandlerController handler = new AGlobalExceptionHandlerController();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    AGlobalExceptionHandlerControllerTest() {
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/test"));
    }

    @Test
    void mapsConstraintViolationToParameterErrorInsteadOf500() {
        ResponseVO response = (ResponseVO) handler.handleException(
                new ConstraintViolationException("groupId must not be empty", Set.of()), request);

        assertEquals(600, response.getCode());
    }

    @Test
    void mapsMissingRequestParameterToParameterError() {
        ResponseVO response = (ResponseVO) handler.handleException(
                new MissingServletRequestParameterException("contactId", "String"), request);

        assertEquals(600, response.getCode());
    }

    @Test
    void keepsDuplicateKeyAndUnknownErrorsOnTheirOwnCodes() {
        ResponseVO duplicate = (ResponseVO) handler.handleException(new DuplicateKeyException("dup"), request);
        ResponseVO unknown = (ResponseVO) handler.handleException(new IllegalStateException("boom"), request);

        assertEquals(601, duplicate.getCode());
        assertEquals(500, unknown.getCode());
    }
}
