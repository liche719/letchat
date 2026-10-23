package com.letchat.entity.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimplePageTest {

    @Test
    void clampsOversizedPageSizeToUpperBound() {
        SimplePage page = new SimplePage(1, 1_000_000, 1_000_000);

        assertEquals(SimplePage.MAX_PAGE_SIZE, page.getPageSize());
        assertEquals(0, page.getStart());
        assertEquals(SimplePage.MAX_PAGE_SIZE, page.getEnd());
    }

    @Test
    void fallsBackToDefaultWhenPageSizeIsNotPositive() {
        SimplePage page = new SimplePage(1, 100, 0);

        assertEquals(20, page.getPageSize());
    }

    @Test
    void keepsNormalPageSizeUntouched() {
        SimplePage page = new SimplePage(2, 200, 30);

        assertEquals(30, page.getPageSize());
        assertEquals(30, page.getStart());
        assertEquals(7, page.getPageTotal());
    }
}
