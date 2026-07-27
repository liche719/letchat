package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GroupInfoControllerSecurityTest {

    @Test
    void everyGroupLifecycleEndpointRequiresLogin() {
        Set<String> protectedMethods = Arrays.stream(GroupInfoController.class.getDeclaredMethods())
                .filter(method -> Arrays.asList(
                        "saveGroup", "loadMyGroup", "getGroupInfo", "getGroupInfo4chat",
                        "addOrRemoveGroupUser", "leaveGroup", "dissolutionGroup"
                ).contains(method.getName()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        for (String methodName : protectedMethods) {
            Method method = Arrays.stream(GroupInfoController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertNotNull(method.getAnnotation(GlobalInterception.class), methodName + " must require login");
        }
    }
}
