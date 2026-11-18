package com.ape.badminton.controller;

import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.AuthContext;
import com.ape.badminton.common.BizException;
import com.ape.badminton.common.OwnershipGuard;
import com.ape.badminton.common.QueryParams;
import com.ape.badminton.entity.Match;
import com.ape.badminton.entity.Player;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.service.DrawService;
import com.ape.badminton.service.EntryService;
import com.ape.badminton.service.MatchService;
import com.ape.badminton.service.TournamentService;
import com.ape.badminton.utils.IdGen;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 赛事接口
 *
 * 「我创建的赛事」由 creator_id 隔离：新增时自动写入当前发起者，
 * 列表默认只返回当前发起者创建的赛事。
 */
@RestController
@RequestMapping("/api/tournament")
public class TournamentController {

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private EntryService entryService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private DrawService drawService;

    @Autowired
    private OwnershipGuard guard;

    /**
     * 我创建的赛事列表
     *
     * 数据严格按 creator_id 隔离：接口不存在「查看他人赛事」的口子。
     * status / keyword 需先清洗：小程序会把 undefined 原样作为查询串发过来，
     * 若当成真实筛选值会导致列表恒为空。
     */
    @GetMapping("/getAll")
    public ApiResponse<List<Tournament>> getAll(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(
                tournamentService.list(QueryParams.text(status), QueryParams.text(keyword), true));
    }

    @GetMapping("/getById/{id}")
    public ApiResponse<Tournament> getById(@PathVariable String id) {
        guard.requireTournament(id);
        Tournament t = tournamentService.getById(id);
        if (t == null) throw new BizException(40400, "赛事不存在");
        return ApiResponse.ok(t);
    }

    @PostMapping("/add")
    public ApiResponse<Tournament> add(@RequestBody Tournament t) {
        validate(t);
        t.setTournament_id(IdGen.next("t"));
        if (t.getCreator_id() == null || t.getCreator_id().isEmpty()) {
            t.setCreator_id(AuthContext.userId());
        }
        // 名单尚未确定 → 草稿；已有参赛名单 → 待编排
        if (t.getStatus() == null || t.getStatus().trim().isEmpty()) {
            t.setStatus("READY");
        }
        applyDefaults(t);
        t.setCreate_time(new Date());
        tournamentService.add(t);
        return ApiResponse.ok(t);
    }

    @PutMapping("/update")
    public ApiResponse<Boolean> update(@RequestBody Tournament t) {
        guard.requireTournament(t.getTournament_id());
        // 归属不可被前端改写
        t.setCreator_id(AuthContext.userId());
        return ApiResponse.ok(tournamentService.update(t));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Boolean> delete(@PathVariable String id) {
        guard.requireTournament(id);
        matchService.deleteByTournament(id);
        return ApiResponse.ok(tournamentService.delete(id));
    }

    /** 赛事详情聚合：基础信息 + 参赛名单 + 场次进度 + 抽签留痕 */
    @GetMapping("/detail/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        guard.requireTournament(id);
        Tournament t = tournamentService.getById(id);
        if (t == null) throw new BizException(40400, "赛事不存在");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tournament", t);

        List<Player> players = entryService.players(id);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("total", entryService.countByTournament(id));
        entry.put("seed_count", countSeed(players));
        List<Map<String, Object>> roster = new ArrayList<>();
        for (Player p : players) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("player_id", p.getPlayer_id());
            r.put("name", p.getName());
            r.put("student_id", p.getStudent_id());
            r.put("gender", p.getGender());
            r.put("team", p.getTeam());
            r.put("level", p.getLevel());
            r.put("phone", p.getPhone());
            r.put("is_seed", Boolean.TRUE.equals(p.getIs_seed()));
            r.put("seed_rank", p.getSeed_rank() == null ? 0 : p.getSeed_rank());
            roster.add(r);
        }
        entry.put("players", roster);
        m.put("entries", entry);

        List<Match> matches = matchService.rawByTournament(id);
        int total = 0, done = 0, bye = 0;
        for (Match x : matches) {
            if ("BYE".equals(x.getStatus())) {
                bye++;
                continue;
            }
            total++;
            if ("DONE".equals(x.getStatus()) || "WALKOVER".equals(x.getStatus())) done++;
        }
        Map<String, Object> prog = new LinkedHashMap<>();
        prog.put("match_total", total);
        prog.put("match_done", done);
        prog.put("match_bye", bye);
        prog.put("progress", total == 0 ? 0 : Math.round(done * 1000.0 / total) / 10.0);
        m.put("progress", prog);

        m.put("logs", drawService.logs(id));
        return ApiResponse.ok(m);
    }

    /** 更新赛事状态 */
    @PutMapping("/status/{id}")
    public ApiResponse<Boolean> updateStatus(@PathVariable String id, @RequestParam String status) {
        guard.requireTournament(id);
        Tournament t = tournamentService.getById(id);
        if (t == null) throw new BizException(40400, "赛事不存在");
        t.setStatus(status);
        return ApiResponse.ok(tournamentService.update(t));
    }

    private int countSeed(List<Player> players) {
        int n = 0;
        for (Player p : players) {
            if (Boolean.TRUE.equals(p.getIs_seed())) n++;
        }
        return n;
    }

    private void validate(Tournament t) {
        if (t.getTitle() == null || t.getTitle().trim().isEmpty()) {
            throw new BizException(40001, "赛事名称不能为空");
        }
        String format = t.getFormat();
        // 单循环赛已下线：给出明确原因，而不是笼统的「不支持」
        if ("ROUND_ROBIN".equals(format)) {
            throw new BizException(40001, "单循环赛已下线，请选择单淘汰赛或小组循环 + 交叉淘汰");
        }
        if (format != null && !format.isEmpty()
                && !"SINGLE_ELIM".equals(format) && !"GROUP_KNOCKOUT".equals(format)) {
            throw new BizException(40001, "不支持的赛制类型");
        }
    }

    private void applyDefaults(Tournament t) {
        if (t.getFormat() == null || t.getFormat().isEmpty()) t.setFormat("SINGLE_ELIM");
        if (t.getEvent_type() == null || t.getEvent_type().isEmpty()) t.setEvent_type("MS");
        if (t.getSeed_count() == null) t.setSeed_count(4);
        if (t.getGroup_advance() == null) t.setGroup_advance(2);
        if (t.getGroup_count() == null) t.setGroup_count(0);
        if (t.getBest_of() == null) t.setBest_of(3);
        // 五局三胜已下线：只保留 1 局定胜负 / 3 局两胜，历史或异常值统一归一到 3 局
        if (t.getBest_of() != 1 && t.getBest_of() != 3) t.setBest_of(3);
        if (t.getPoints_per_game() == null) t.setPoints_per_game(21);
        if (t.getPoints_cap() == null) t.setPoints_cap(30);
        if (t.getAvoid_same_team() == null) t.setAvoid_same_team(true);
        if (t.getActual_participants() == null) t.setActual_participants(0);
        if (t.getBracket_size() == null) t.setBracket_size(0);
        if (t.getBye_count() == null) t.setBye_count(0);
    }
}
