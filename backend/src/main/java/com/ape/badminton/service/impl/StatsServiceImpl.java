package com.ape.badminton.service.impl;

import com.ape.badminton.algorithm.BracketBuilder;
import com.ape.badminton.algorithm.GroupKnockoutEngine;
import com.ape.badminton.algorithm.RoundRobinEngine;
import com.ape.badminton.algorithm.Standing;
import com.ape.badminton.common.AuthContext;
import com.ape.badminton.entity.GroupEntry;
import com.ape.badminton.entity.Match;
import com.ape.badminton.entity.Player;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.mapper.GroupMapper;
import com.ape.badminton.mapper.PlayerMapper;
import com.ape.badminton.service.EntryService;
import com.ape.badminton.service.PlayerService;
import com.ape.badminton.service.StatsService;
import com.ape.badminton.service.TournamentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计与看板
 *
 * 所有统计口径都限定在「当前比赛发起者」自己的数据范围内：
 *   我的选手库 / 我创建的赛事 / 我赛事中的场次进度
 */
@Service
public class StatsServiceImpl implements StatsService {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private EntryService entryService;

    @Autowired
    private com.ape.badminton.service.MatchService matchService;

    @Autowired
    private GroupMapper groupMapper;

    @Override
    public Map<String, Object> overview() {
        String me = AuthContext.userId();
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> teams = playerService.listTeams();
        m.put("player_count", playerService.count());
        m.put("team_count", teams.size());
        m.put("teams", teams);
        m.put("seed_count", countSeeds());
        m.put("tournament_count", me == null ? 0 : tournamentService.countByCreator(me));
        m.put("ready_count", me == null ? 0 : tournamentService.countByCreatorAndStatus(me, "READY"));
        m.put("ongoing_count", (me == null ? 0
                : tournamentService.countByCreatorAndStatus(me, "ONGOING")
                + tournamentService.countByCreatorAndStatus(me, "DRAWN")));
        m.put("finished_count", me == null ? 0 : tournamentService.countByCreatorAndStatus(me, "FINISHED"));
        m.put("pending_score", countPendingMatches());
        return m;
    }

    /** 我赛事中「可开赛但未完成」的场次数，用于首页「录入比分」提醒 */
    private long countPendingMatches() {
        String me = AuthContext.userId();
        if (me == null) return 0;
        long n = 0;
        for (Tournament t : tournamentService.list(null, null, true)) {
            for (Match x : matchService.rawByTournament(t.getTournament_id())) {
                if (BracketBuilder.STATUS_READY.equals(x.getStatus())
                        || BracketBuilder.STATUS_PENDING.equals(x.getStatus())) {
                    n++;
                }
            }
        }
        return n;
    }

    private long countSeeds() {
        String me = AuthContext.userId();
        if (me == null) return 0;
        QueryWrapper<Player> w = new QueryWrapper<>();
        w.eq("creator_id", me).eq("is_seed", true);
        return playerMapper.selectCount(w);
    }

    @Override
    public List<Standing> standings(String tournamentId) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) return new ArrayList<>();
        List<Player> players = entryService.players(tournamentId);
        List<String> ids = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        Map<String, String> teams = new HashMap<>();
        for (Player p : players) {
            ids.add(p.getPlayer_id());
            names.put(p.getPlayer_id(), p.getName());
            teams.put(p.getPlayer_id(), p.getTeam());
        }
        List<Match> matches = matchService.rawByTournament(tournamentId);
        return RoundRobinEngine.standings(ids, matches, names, teams);
    }

    @Override
    public List<Map<String, Object>> groupStandings(String tournamentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        QueryWrapper<GroupEntry> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId).orderByAsc("group_no").orderByAsc("seed_position");
        List<GroupEntry> entries = groupMapper.selectList(w);
        if (entries.isEmpty()) return out;

        Map<Integer, List<String>> byGroup = new LinkedHashMap<>();
        for (GroupEntry e : entries) {
            byGroup.computeIfAbsent(e.getGroup_no(), k -> new ArrayList<>()).add(e.getPlayer_id());
        }

        List<Match> all = matchService.rawByTournament(tournamentId);
        Map<String, String> names = new HashMap<>();
        Map<String, String> teams = new HashMap<>();
        for (GroupEntry e : entries) {
            Player p = playerMapper.selectById(e.getPlayer_id());
            if (p != null) {
                names.put(p.getPlayer_id(), p.getName());
                teams.put(p.getPlayer_id(), p.getTeam());
            }
        }

        Tournament t = tournamentService.getById(tournamentId);
        int advance = t == null || t.getGroup_advance() == null ? 2 : t.getGroup_advance();

        for (Map.Entry<Integer, List<String>> e : byGroup.entrySet()) {
            List<Match> gm = new ArrayList<>();
            for (Match m : all) {
                if (m.getGroup_no() != null && m.getGroup_no().equals(e.getKey())
                        && ("GROUP".equals(m.getStage()) || "ROUND_ROBIN".equals(m.getStage()))) {
                    gm.add(m);
                }
            }
            List<Standing> st = RoundRobinEngine.standings(e.getValue(), gm, names, teams);
            boolean groupDone = true;
            for (Match m : gm) {
                if (!BracketBuilder.STATUS_DONE.equals(m.getStatus())
                        && !BracketBuilder.STATUS_BYE.equals(m.getStatus())
                        && !"WALKOVER".equals(m.getStatus())) {
                    groupDone = false;
                    break;
                }
            }
            if (groupDone) {
                for (int i = 0; i < st.size(); i++) {
                    st.get(i).setAdvanced(i < advance);
                }
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("group_no", e.getKey());
            g.put("group_name", GroupKnockoutEngine.groupName(e.getKey()));
            g.put("finished", groupDone);
            g.put("advance", advance);
            g.put("standings", st);
            out.add(g);
        }
        out.sort(Comparator.comparingInt(x -> (Integer) x.get("group_no")));
        return out;
    }

    @Override
    public Map<String, Object> report(String tournamentId) {
        Map<String, Object> r = new LinkedHashMap<>();
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) return r;

        List<Match> all = matchService.rawByTournament(tournamentId);
        int total = 0, done = 0, bye = 0;
        long totalPoints = 0;
        for (Match m : all) {
            if (BracketBuilder.STATUS_BYE.equals(m.getStatus())) {
                bye++;
                continue;
            }
            total++;
            if (BracketBuilder.STATUS_DONE.equals(m.getStatus()) || "WALKOVER".equals(m.getStatus())) {
                done++;
                totalPoints += nz(m.getScore_a()) + nz(m.getScore_b());
            }
        }

        List<Player> players = entryService.players(tournamentId);
        Map<String, Integer> teamDist = new LinkedHashMap<>();
        Map<String, Integer> genderDist = new LinkedHashMap<>();
        for (Player p : players) {
            String team = p.getTeam() == null || p.getTeam().isEmpty() ? "未填写" : p.getTeam();
            teamDist.merge(team, 1, Integer::sum);
            String g = "F".equalsIgnoreCase(p.getGender()) ? "女子" : "男子";
            genderDist.merge(g, 1, Integer::sum);
        }

        r.put("tournament", t);
        r.put("participants", players.size());
        r.put("team_distribution", teamDist);
        r.put("gender_distribution", genderDist);
        r.put("match_total", total);
        r.put("match_done", done);
        r.put("match_pending", total - done);
        r.put("match_bye", bye);
        r.put("progress", total == 0 ? 0 : Math.round(done * 1000.0 / total) / 10.0);
        r.put("avg_points_per_match", done == 0 ? 0 : Math.round(totalPoints * 1.0 / done));
        r.put("podium", podium(t));
        r.put("standings", standings(tournamentId));
        if ("GROUP_KNOCKOUT".equals(t.getFormat())) {
            r.put("groups", groupStandings(tournamentId));
        }
        return r;
    }

    private List<Map<String, Object>> podium(Tournament t) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(podiumItem("冠军", t.getChampion_id()));
        list.add(podiumItem("亚军", t.getRunner_up_id()));
        list.add(podiumItem("季军", t.getThird_id()));
        return list;
    }

    private Map<String, Object> podiumItem(String label, String playerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("player_id", playerId);
        if (playerId != null) {
            Player p = playerMapper.selectById(playerId);
            m.put("player_name", p == null ? playerId : p.getName());
            m.put("team", p == null ? "" : p.getTeam());
        } else {
            m.put("player_name", null);
            m.put("team", null);
        }
        return m;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
