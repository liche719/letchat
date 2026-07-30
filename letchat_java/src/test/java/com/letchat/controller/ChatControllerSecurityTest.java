package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatControllerSecurityTest {

    @Test
    void mutatingAndHistoryEndpointsRequireLogin() {
        Set<String> protectedMethods = Arrays.stream(ChatController.class.getDeclaredMethods())
                .filter(method -> Arrays.asList(
                        "sendMessage",
                        "uploadFile",
                        "loadChatMessage",
                        "syncMessages",
                        "acknowledgeDelivered",
                        "acknowledgeRead"
                ).contains(method.getName()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        for (String methodName : protectedMethods) {
            Method method = Arrays.stream(ChatController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertNotNull(method.getAnnotation(GlobalInterception.class), methodName + " must require login");
        }
    }
}
