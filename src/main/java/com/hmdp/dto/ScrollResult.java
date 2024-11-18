package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
/**
 * 分页滚吨的结果
 */
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
