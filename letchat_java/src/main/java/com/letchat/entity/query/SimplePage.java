package com.letchat.entity.query;

import com.letchat.entity.enums.PageSize;
import lombok.Data;

@Data
public class SimplePage {
    /** 请求可控的分页大小上限。 */
    public static final int MAX_PAGE_SIZE = 100;

    private int pageNo;
    private int countTotal;
    private int pageSize;
    private int pageTotal;
    private int start;
    private int end;

    public SimplePage() {
    }

    public SimplePage(Integer pageNo, int countTotal, int pageSize) {
        if (null == pageNo) {
            pageNo = 0;
        }
        this.pageNo = pageNo;
        this.countTotal = countTotal;
        this.pageSize = pageSize;
        action();
    }

    public SimplePage(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public void action() {
        if (this.pageSize <= 0) {
            this.pageSize = PageSize.SIZE20.getSize();
        }
        // 分页大小来自请求参数时必须 clamp：否则传 pageSize=1000000 会一次拉全表进内存。
        if (this.pageSize > MAX_PAGE_SIZE) {
            this.pageSize = MAX_PAGE_SIZE;
        }
        if (this.countTotal > 0) {
            this.pageTotal = this.countTotal % this.pageSize == 0 ? this.countTotal / this.pageSize
                    : this.countTotal / this.pageSize + 1;
        } else {
            pageTotal = 1;
        }

        if (pageNo <= 1) {
            pageNo = 1;
        }
        if (pageNo > pageTotal) {
            pageNo = pageTotal;
        }
        this.start = (pageNo - 1) * pageSize;
        this.end = this.pageSize;
    }

    public void setCountTotal(int countTotal) {
        this.countTotal = countTotal;
        this.action();
    }

}
