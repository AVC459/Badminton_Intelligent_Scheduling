package com.ape.badminton.controller;

import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.BizException;
import com.ape.badminton.common.OwnershipGuard;
import com.ape.badminton.entity.Entry;
import com.ape.badminton.entity.Player;
import com.ape.badminton.service.EntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参赛名单（无报名流程：发起者从选手库直接选拔）
 */
@RestController
@RequestMapping("/api/entry")
public class EntryController {

    @Autowired
    private EntryService entryService;

    @Autowired
    private OwnershipGuard guard;

    /** 参赛名单（含选手档案 + 赛事级种子信息） */
    @GetMapping("/list/{tournamentId}")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        List<Entry> entries = entryService.listByTournament(tournamentId);
        Map<String, Player> players = new LinkedHashMap<>();
        for (Player p : entryService.players(tournamentId)) {
            players.put(p.getPlayer_id(), p);
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entry_id", e.getEntry_id());
            m.put("tournament_id", e.getTournament_id());
            m.put("player_id", e.getPlayer_id());
            m.put("seed_flag", Boolean.TRUE.equals(e.getSeed_flag()));
            m.put("seed_rank", e.getSeed_rank() == null ? 0 : e.getSeed_rank());
            m.put("create_time", e.getCreate_time());
            Player p = players.get(e.getPlayer_id());
            if (p != null) {
                m.put("name", p.getName());
                m.put("student_id", p.getStudent_id());
                m.put("gender", p.getGender());
                m.put("team", p.getTeam());
                m.put("phone", p.getPhone());
                m.put("level", p.getLevel());
            }
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** 参赛选手档案（抽签池） */
    @GetMapping("/players/{tournamentId}")
    public ApiResponse<List<Player>> players(@PathVariable String tournamentId) {
        guard.requireTournament(tournamentId);
        return ApiResponse.ok(entryService.players(tournamentId));
    }

    /** 批量选入参赛名单 */
    @PostMapping("/add")
    public ApiResponse<Integer> add(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("tournament_id") == null) {
            throw new BizException(40001, "缺少赛事ID");
        }
        guard.requireTournament(String.valueOf(body.get("tournament_id")));
        Object raw = body.get("player_ids");
        List<String> ids = new java.util.ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o != null) ids.add(String.valueOf(o));
            }
        }
        return ApiResponse.ok(entryService.add(String.valueOf(body.get("tournament_id")), ids));
    }

    /** 移出参赛名单 */
    @DeleteMapping("/remove/{entryId}")
    public ApiResponse<Boolean> remove(@PathVariable String entryId) {
        guard.requireTournament(tournamentOf(entryId));
        return ApiResponse.ok(entryService.remove(entryId));
    }

    /** 设置 / 取消赛事级种子 */
    @PutMapping("/seed/{entryId}")
    public ApiResponse<Boolean> setSeed(@PathVariable String entryId,
                                        @RequestParam(required = false) Boolean seedFlag,
                                        @RequestParam(required = false) Integer seedRank) {
        guard.requireTournament(tournamentOf(entryId));
        return ApiResponse.ok(entryService.setSeed(entryId, seedFlag, seedRank));
    }

    /** 取参赛记录所属赛事（供归属校验使用） */
    private String tournamentOf(String entryId) {
        Entry e = entryService.getById(entryId);
        if (e == null) {
            throw new BizException(40400, "参赛记录不存在");
        }
        return e.getTournament_id();
    }
}
