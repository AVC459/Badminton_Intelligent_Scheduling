package com.ape.badminton.controller;

import com.ape.badminton.algorithm.DrawResult;
import com.ape.badminton.algorithm.FairnessReport;
import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.OwnershipGuard;
import com.ape.badminton.dto.DrawConfigDto;
import com.ape.badminton.dto.DrawOutcomeVo;
import com.ape.badminton.entity.DrawLog;
import com.ape.badminton.service.DrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/draw")
public class DrawController {

    @Autowired
    private DrawService drawService;

    @Autowired
    private OwnershipGuard guard;

    /** 试算抽签（不落库），用于前端实时预览规模与种子分布 */
    @PostMapping("/preview/{tournamentId}")
    public ApiResponse<DrawResult> preview(@PathVariable String tournamentId,
                                           @RequestBody(required = false) DrawConfigDto config) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(drawService.preview(tournamentId, config));
    }

    /** 正式执行抽签编排 */
    @PostMapping("/execute/{tournamentId}")
    public ApiResponse<DrawOutcomeVo> execute(@PathVariable String tournamentId,
                                              @RequestBody(required = false) DrawConfigDto config) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(drawService.execute(tournamentId, config));
    }

    /** 抽签过程留痕（可回溯、可公示） */
    @GetMapping("/logs/{tournamentId}")
    public ApiResponse<List<DrawLog>> logs(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(drawService.logs(tournamentId));
    }

    /** 公平性报告 */
    @GetMapping("/fairness/{tournamentId}")
    public ApiResponse<FairnessReport> fairness(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(drawService.fairness(tournamentId));
    }

    /** 小组赛结束后生成交叉淘汰赛程 */
    @PostMapping("/generateKnockout/{tournamentId}")
    public ApiResponse<Map<String, Object>> generateKnockout(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(drawService.generateKnockout(tournamentId));
    }

    /** 撤销抽签 */
    @PostMapping("/reset/{tournamentId}")
    public ApiResponse<Boolean> reset(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(drawService.reset(tournamentId));
    }
}
