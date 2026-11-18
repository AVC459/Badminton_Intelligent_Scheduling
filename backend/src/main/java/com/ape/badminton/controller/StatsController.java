package com.ape.badminton.controller;

import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.OwnershipGuard;
import com.ape.badminton.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @Autowired
    private OwnershipGuard guard;

    /** 首页数据看板（统计口径为当前登录发起者） */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(statsService.overview());
    }

    /** 赛事报告（进度、场地、单位分布、冠亚季军等） */
    @GetMapping("/report/{tournamentId}")
    public ApiResponse<Map<String, Object>> report(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(statsService.report(tournamentId));
    }
}
