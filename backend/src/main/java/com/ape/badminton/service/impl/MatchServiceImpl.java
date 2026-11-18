package com.ape.badminton.service.impl;

import com.ape.badminton.algorithm.BracketBuilder;
import com.ape.badminton.algorithm.DrawEngine;
import com.ape.badminton.algorithm.GroupKnockoutEngine;
import com.ape.badminton.common.BizException;
import com.ape.badminton.dto.MatchVo;
import com.ape.badminton.dto.ScoreDto;
import com.ape.badminton.entity.Match;
import com.ape.badminton.entity.Player;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.mapper.MatchMapper;
import com.ape.badminton.mapper.PlayerMapper;
import com.ape.badminton.service.MatchService;
import com.ape.badminton.service.TournamentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MatchServiceImpl implements MatchService {

    private static final Map<String, String> STAGE_LABEL = new HashMap<>();
    private static final Map<String, String> STATUS_LABEL = new HashMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        STAGE_LABEL.put("GROUP", "小组赛");
        STAGE_LABEL.put("KNOCKOUT", "淘汰赛");
        STAGE_LABEL.put("ROUND_ROBIN", "循环赛");
        STAGE_LABEL.put("THIRD_PLACE", "季军赛");

        STATUS_LABEL.put("PENDING", "待就绪");
        STATUS_LABEL.put("READY", "可开赛");
        STATUS_LABEL.put("DONE", "已完成");
        STATUS_LABEL.put("BYE", "轮空");
        STATUS_LABEL.put("WALKOVER", "弃权");
    }

    @Autowired
    private MatchMapper matchMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private TournamentService tournamentService;

    @Override
    public List<Match> rawByTournament(String tournamentId) {
        QueryWrapper<Match> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId);
        w.orderByAsc("round").orderByAsc("group_no").orderByAsc("match_order");
        return matchMapper.selectList(w);
    }

    @Override
    public List<MatchVo> listByTournament(String tournamentId, String stage, Integer groupNo, String status) {
        QueryWrapper<Match> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId);
        if (stage != null && !stage.trim().isEmpty()) w.eq("stage", stage.trim());
        if (groupNo != null && groupNo > 0) w.eq("group_no", groupNo);
        if (status != null && !status.trim().isEmpty()) w.eq("status", status.trim());
        w.orderByAsc("round").orderByAsc("group_no").orderByAsc("match_order");
        return toVos(matchMapper.selectList(w));
    }

    @Override
    public MatchVo getVo(String matchId) {
        Match m = matchMapper.selectById(matchId);
        if (m == null) throw new BizException(40400, "场次不存在");
        List<MatchVo> vos = toVos(java.util.Collections.singletonList(m));
        return vos.isEmpty() ? null : vos.get(0);
    }

    @Override
    public Match getById(String matchId) {
        return matchMapper.selectById(matchId);
    }

    @Override
    public void saveAll(List<Match> matches) {
        if (matches == null) return;
        for (Match m : matches) {
            matchMapper.insert(m);
        }
    }

    @Override
    public void deleteByTournament(String tournamentId) {
        QueryWrapper<Match> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId);
        matchMapper.delete(w);
    }

    @Override
    public void deleteByTournamentAndStage(String tournamentId, String stage) {
        QueryWrapper<Match> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId).eq("stage", stage);
        matchMapper.delete(w);
    }

    // ========================================================================
    //  记分
    // ========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchVo saveScore(ScoreDto dto) {
        if (dto == null || dto.getMatchId() == null) {
            throw new BizException(40001, "缺少场次ID");
        }
        Match m = matchMapper.selectById(dto.getMatchId());
        if (m == null) throw new BizException(40400, "场次不存在");
        if (DrawEngine.BYE.equals(m.getPlayer_a_id()) || DrawEngine.BYE.equals(m.getPlayer_b_id())) {
            throw new BizException(40002, "轮空场次无需记分");
        }
        if (m.getPlayer_a_id() == null || m.getPlayer_b_id() == null) {
            throw new BizException(40002, "对阵尚未确定，暂时无法记分");
        }

        Tournament t = tournamentService.getById(m.getTournament_id());
        int bestOf = t == null || t.getBest_of() == null ? 3 : t.getBest_of();
        int need = bestOf / 2 + 1;

        String winnerId;
        String loserId;

        if (Boolean.TRUE.equals(dto.getWalkover())) {
            // 弃权：retiredPlayerId 为退赛方；未指定则判 A 方获胜
            String retired = dto.getRetiredPlayerId();
            if (retired != null && retired.equals(m.getPlayer_a_id())) {
                winnerId = m.getPlayer_b_id();
            } else if (retired != null && retired.equals(m.getPlayer_b_id())) {
                winnerId = m.getPlayer_a_id();
            } else {
                winnerId = m.getPlayer_a_id();
            }
            loserId = winnerId.equals(m.getPlayer_a_id()) ? m.getPlayer_b_id() : m.getPlayer_a_id();
            m.setStatus("WALKOVER");
            m.setGames_a(winnerId.equals(m.getPlayer_a_id()) ? need : 0);
            m.setGames_b(winnerId.equals(m.getPlayer_b_id()) ? need : 0);
            m.setGames_json("[]");
            m.setRemark(dto.getRemark() == null ? "对方弃权" : dto.getRemark());
        } else {
            List<ScoreDto.GameScore> games = dto.getGames();
            if (games == null || games.isEmpty()) {
                throw new BizException(40001, "请至少录入一局比分");
            }
            int ga = 0, gb = 0, pa = 0, pb = 0;
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < games.size(); i++) {
                ScoreDto.GameScore g = games.get(i);
                int a = g.getA() == null ? 0 : g.getA();
                int b = g.getB() == null ? 0 : g.getB();
                if (a == b) throw new BizException(40001, "第 " + (i + 1) + " 局比分不能相同");
                if (a > b) ga++;
                else gb++;
                pa += a;
                pb += b;
                if (i > 0) json.append(",");
                json.append("{\"a\":").append(a).append(",\"b\":").append(b).append("}");
            }
            json.append("]");

            if (ga == gb) throw new BizException(40001, "局分平分，无法判定胜负");
            if (Math.max(ga, gb) < need) {
                throw new BizException(40001, "本场为 " + bestOf + " 局 " + need + " 胜制，胜方需赢下 " + need + " 局");
            }
            winnerId = ga > gb ? m.getPlayer_a_id() : m.getPlayer_b_id();
            loserId = ga > gb ? m.getPlayer_b_id() : m.getPlayer_a_id();
            m.setGames_a(ga);
            m.setGames_b(gb);
            m.setScore_a(pa);
            m.setScore_b(pb);
            m.setGames_json(json.toString());
            m.setStatus("DONE");
            if (dto.getRemark() != null) m.setRemark(dto.getRemark());
        }

        m.setWinner_id(winnerId);
        m.setLoser_id(loserId);
        matchMapper.updateById(m);

        // 自动晋级
        List<Match> all = rawByTournament(m.getTournament_id());
        BracketBuilder.advance(m, all, winnerId, false);
        BracketBuilder.advance(m, all, loserId, true);
        // 逐轮配对签表中，轮空名额可能落在「上一轮的胜者」上：
        // 该胜者刚被填入轮空场次后，需要立刻按轮空规则自动晋级到下一轮。
        BracketBuilder.resolveByes(all);
        BracketBuilder.refreshStatus(all);
        for (Match x : all) {
            if (!x.getMatch_id().equals(m.getMatch_id())) {
                matchMapper.updateById(x);
            }
        }

        syncTournamentStatus(m.getTournament_id(), all);
        return getVo(m.getMatch_id());
    }

    /** 全部场次打完 → 赛事结束，并记录冠亚季军 */
    private void syncTournamentStatus(String tournamentId, List<Match> all) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) return;
        boolean allDone = true;
        for (Match m : all) {
            String s = m.getStatus();
            if (!BracketBuilder.STATUS_DONE.equals(s) && !BracketBuilder.STATUS_BYE.equals(s)
                    && !"WALKOVER".equals(s)) {
                allDone = false;
                break;
            }
        }
        // 记录决赛/季军赛结果
        for (Match m : all) {
            if (BracketBuilder.STAGE_KNOCKOUT.equals(m.getStage()) && "决赛".equals(m.getRound_name())) {
                if (m.getWinner_id() != null) {
                    t.setChampion_id(m.getWinner_id());
                    t.setRunner_up_id(m.getLoser_id());
                }
            }
            if (BracketBuilder.STAGE_THIRD.equals(m.getStage()) && m.getWinner_id() != null) {
                t.setThird_id(m.getWinner_id());
            }
        }
        // 循环赛制：全部结束即产生冠军
        if ("ROUND_ROBIN".equals(t.getFormat()) && allDone) {
            List<Match> done = new ArrayList<>();
            for (Match m : all) {
                if (BracketBuilder.STATUS_DONE.equals(m.getStatus()) || "WALKOVER".equals(m.getStatus())) {
                    done.add(m);
                }
            }
            Map<String, int[]> board = new HashMap<>();
            for (Match m : done) {
                if (m.getWinner_id() == null) continue;
                board.computeIfAbsent(m.getWinner_id(), k -> new int[1])[0]++;
            }
            String best = null;
            int bestWins = -1;
            for (Map.Entry<String, int[]> e : board.entrySet()) {
                if (e.getValue()[0] > bestWins) {
                    bestWins = e.getValue()[0];
                    best = e.getKey();
                }
            }
            if (best != null) t.setChampion_id(best);
        }
        if (allDone && !"FINISHED".equals(t.getStatus())) {
            t.setStatus("FINISHED");
        } else if (!allDone && ("DRAWN".equals(t.getStatus()) || "REGISTERING".equals(t.getStatus()))) {
            boolean anyDone = false;
            for (Match m : all) {
                if (BracketBuilder.STATUS_DONE.equals(m.getStatus()) || "WALKOVER".equals(m.getStatus())) {
                    anyDone = true;
                    break;
                }
            }
            if (anyDone) t.setStatus("ONGOING");
        }
        tournamentService.update(t);
    }

    // ========================================================================
    //  VO 组装
    // ========================================================================

    private List<MatchVo> toVos(List<Match> list) {
        List<MatchVo> out = new ArrayList<>();
        if (list == null) return out;
        Map<String, Player> cache = new HashMap<>();
        for (Match m : list) {
            MatchVo vo = new MatchVo();
            vo.setMatch_id(m.getMatch_id());
            vo.setTournament_id(m.getTournament_id());
            vo.setStage(m.getStage());
            vo.setStage_label(STAGE_LABEL.getOrDefault(m.getStage(), m.getStage()));
            vo.setRound(m.getRound());
            vo.setRound_name(m.getRound_name());
            vo.setGroup_no(m.getGroup_no());
            if (m.getGroup_no() != null && m.getGroup_no() > 0) {
                vo.setGroup_name(GroupKnockoutEngine.groupName(m.getGroup_no()));
            }
            vo.setMatch_order(m.getMatch_order());

            vo.setPlayer_a_id(m.getPlayer_a_id());
            vo.setPlayer_b_id(m.getPlayer_b_id());
            vo.setPlayer_a_name(nameOf(m.getPlayer_a_id(), cache));
            vo.setPlayer_b_name(nameOf(m.getPlayer_b_id(), cache));
            vo.setPlayer_a_team(teamOf(m.getPlayer_a_id(), cache));
            vo.setPlayer_b_team(teamOf(m.getPlayer_b_id(), cache));

            vo.setWinner_id(m.getWinner_id());
            vo.setWinner_name(nameOf(m.getWinner_id(), cache));
            vo.setGames_json(m.getGames_json());
            vo.setGames(parseGames(m.getGames_json()));
            vo.setScore_a(m.getScore_a());
            vo.setScore_b(m.getScore_b());
            vo.setGames_a(m.getGames_a());
            vo.setGames_b(m.getGames_b());
            vo.setStatus(m.getStatus());
            vo.setStatus_label(STATUS_LABEL.getOrDefault(m.getStatus(), m.getStatus()));
            vo.setNext_match_id(m.getNext_match_id());
            vo.setNext_slot(m.getNext_slot());
            vo.setLoser_next_id(m.getLoser_next_id());
            vo.setLoser_slot(m.getLoser_slot());
            vo.setRemark(m.getRemark());
            out.add(vo);
        }
        return out;
    }

    private List<MatchVo.GameScoreVo> parseGames(String json) {
        List<MatchVo.GameScoreVo> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) return list;
        try {
            List<Map<String, Object>> raw = JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
            for (Map<String, Object> g : raw) {
                MatchVo.GameScoreVo v = new MatchVo.GameScoreVo();
                v.setA(toInt(g.get("a")));
                v.setB(toInt(g.get("b")));
                list.add(v);
            }
        } catch (Exception ignore) {
            // 比分格式异常时忽略，返回空列表而非抛错，保证列表页可用
        }
        return list;
    }

    private static Integer toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private Player player(String id, Map<String, Player> cache) {
        if (id == null || DrawEngine.BYE.equals(id)) return null;
        if (cache.containsKey(id)) return cache.get(id);
        Player p = playerMapper.selectById(id);
        cache.put(id, p);
        return p;
    }

    private String nameOf(String id, Map<String, Player> cache) {
        if (id == null) return null;
        if (DrawEngine.BYE.equals(id)) return "轮空";
        Player p = player(id, cache);
        return p == null ? id : p.getName();
    }

    private String teamOf(String id, Map<String, Player> cache) {
        Player p = player(id, cache);
        return p == null ? "" : p.getTeam();
    }
}
