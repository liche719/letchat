package com.letchat.entity.enums;

import lombok.Getter;

@Getter
public enum PageSize {

    SIZE15(15), SIZE20(20), SIZE30(30), SIZE40(40), SIZE50(50);

    int size;

    private PageSize(int size) {
        this.size = size;
    }

}
