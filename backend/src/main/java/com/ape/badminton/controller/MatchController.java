package com.ape.badminton.controller;

import com.ape.badminton.algorithm.Standing;
import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.OwnershipGuard;
import com.ape.badminton.common.QueryParams;
import com.ape.badminton.dto.MatchVo;
import com.ape.badminton.dto.ScoreDto;
import com.ape.badminton.service.MatchService;
import com.ape.badminton.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Autowired
    private StatsService statsService;

    @Autowired
    private OwnershipGuard guard;

    /** 场次列表（可按阶段 / 小组 / 状态过滤） */
    @GetMapping("/list/{tournamentId}")
    public ApiResponse<List<MatchVo>> list(@PathVariable String tournamentId,
                                           @RequestParam(required = false) String stage,
                                           @RequestParam(required = false) String groupNo,
                                           @RequestParam(required = false) String status) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(matchService.listByTournament(tournamentId,
                QueryParams.text(stage), QueryParams.integer(groupNo), QueryParams.text(status)));
    }

    @GetMapping("/getById/{matchId}")
    public ApiResponse<MatchVo> getById(@PathVariable String matchId) {
        guard.requireMatch(matchId);
        return ApiResponse.ok(matchService.getVo(matchId));
    }

    /** 录入比分：自动判定胜负并完成晋级 */
    @PostMapping("/score")
    public ApiResponse<MatchVo> score(@RequestBody ScoreDto dto) {
        guard.requireMatch(dto == null ? null : dto.getMatchId());
        return ApiResponse.ok(matchService.saveScore(dto));
    }

    /** 整体排名（循环赛制即最终名次） */
    @GetMapping("/standings/{tournamentId}")
    public ApiResponse<List<Standing>> standings(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(statsService.standings(tournamentId));
    }

    /** 小组积分榜 */
    @GetMapping("/groups/{tournamentId}")
    public ApiResponse<List<Map<String, Object>>> groups(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(statsService.groupStandings(tournamentId));
    }
}
