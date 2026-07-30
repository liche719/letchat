package com.letchat.redis;

import com.letchat.entity.Constants;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.security.SecureTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisComponentWebSocketTicketTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsShortLivedTicketAndConsumesItOnlyOnce() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        SecureTokenGenerator tokenGenerator = mock(SecureTokenGenerator.class);
        when(redis.opsForValue()).thenReturn(values);
        when(tokenGenerator.generate()).thenReturn("ticket-1");
        TokenUserInfoDto user = new TokenUserInfoDto();
        user.setUserId("U1001");
        String key = Constants.REDIS_KEY_WS_TICKET + "ticket-1";
        when(redis.opsForValue().getAndDelete(key)).thenReturn(user).thenReturn(null);
        RedisComponent component = new RedisComponent();
        ReflectionTestUtils.setField(component, "redisTemplate", redis);
        ReflectionTestUtils.setField(component, "secureTokenGenerator", tokenGenerator);
        ReflectionTestUtils.setField(component, "webSocketTicketTtlSeconds", 30L);

        assertEquals("ticket-1", component.createWebSocketTicket(user));
        verify(values).set(key, user, 30L, TimeUnit.SECONDS);
        assertEquals(user, component.consumeWebSocketTicket("ticket-1"));
        assertNull(component.consumeWebSocketTicket("ticket-1"));
    }
}
