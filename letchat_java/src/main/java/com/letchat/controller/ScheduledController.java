package com.letchat.controller;

import com.letchat.entity.vo.ResponseVO;
import com.letchat.service.ScheduledService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;

@Component("scheduledController")
@RequestMapping("/scheduled")
@Slf4j
public class ScheduledController extends ABaseController {

    @Resource
    private ScheduledService scheduledService;



}
