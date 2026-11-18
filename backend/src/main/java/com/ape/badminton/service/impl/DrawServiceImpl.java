package com.ape.badminton.service.impl;

import com.ape.badminton.algorithm.BracketBuilder;
import com.ape.badminton.algorithm.DrawEngine;
import com.ape.badminton.algorithm.DrawRequest;
import com.ape.badminton.algorithm.DrawResult;
import com.ape.badminton.algorithm.FairnessReport;
import com.ape.badminton.algorithm.GroupKnockoutEngine;
import com.ape.badminton.algorithm.RoundRobinEngine;
import com.ape.badminton.algorithm.Standing;
import com.ape.badminton.common.BizException;
import com.ape.badminton.dto.DrawConfigDto;
import com.ape.badminton.dto.DrawOutcomeVo;
import com.ape.badminton.entity.DrawLog;
import com.ape.badminton.entity.GroupEntry;
import com.ape.badminton.entity.Match;
import com.ape.badminton.entity.Player;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.mapper.DrawLogMapper;
import com.ape.badminton.mapper.GroupMapper;
import com.ape.badminton.mapper.PlayerMapper;
import com.ape.badminton.service.DrawService;
import com.ape.badminton.service.EntryService;
import com.ape.badminton.service.MatchService;
import com.ape.badminton.service.StatsService;
import com.ape.badminton.service.TournamentService;
import com.ape.badminton.utils.IdGen;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 抽签编排业务实现
 *
 * 三种赛制的编排入口：
 *   SINGLE_ELIM     —— DrawEngine 抽签 → BracketBuilder 生成淘汰树
 *   ROUND_ROBIN     —— RoundRobinEngine 轮转法生成全部对阵
 *   GROUP_KNOCKOUT  —— 蛇形分组 → 各组单循环 → 出线后交叉淘汰
 */
@Service
public class DrawServiceImpl implements DrawService {

    private final DrawEngine engine = new DrawEngine();

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private EntryService entryService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private StatsService statsService;


    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private DrawLogMapper drawLogMapper;

    // ========================================================================
    //  试算
    // ========================================================================

    @Override
    public DrawResult preview(String tournamentId, DrawConfigDto config) {
        Tournament t = requireTournament(tournamentId);
        List<Player> players = resolvePlayers(t, config);
        if (players.size() < 2) {
            throw new BizException(40002, "参赛选手不足 2 人，无法编排");
        }
        String format = effectiveFormat(t, config);
        int groupCount = config != null && config.getGroupCount() != null ? config.getGroupCount() : 0;
        int advance = config != null && config.getGroupAdvance() != null ? config.getGroupAdvance()
                : (t.getGroup_advance() == null ? 2 : t.getGroup_advance());
        boolean third = config == null || !Boolean.FALSE.equals(config.getThirdPlace());

        DrawResult r;
        if ("SINGLE_ELIM".equals(format)) {
            // 逐轮配对签表：不预置 2^n 签位，签位规模即参赛人数；
            // 轮空只出现在「该轮人数为奇数」时，因此偶数人参赛首轮没有轮空。
            r = new DrawResult();
            r.setBracketSize(players.size());
            r.setByeCount(BracketBuilder.dynamicByeCount(players.size()));
            r.setRounds(BracketBuilder.roundCountOf(players.size()));
        } else {
            r = new DrawResult();
            int size = DrawEngine.bracketSize(players.size());
            r.setBracketSize(size);
            r.setByeCount(size - players.size());
            r.setRounds(DrawEngine.roundsOf(size));
        }

        int effectiveGroups = "GROUP_KNOCKOUT".equals(format)
                ? GroupKnockoutEngine.suggestGroupCount(players.size(), groupCount) : groupCount;
        r.setFormat(format);
        r.setParticipants(players.size());
        r.setGroupCount(effectiveGroups);
        r.setGroupAdvance(advance);
        r.setMatchCount(estimateMatchCount(format, players.size(), groupCount, advance, third));
        r.addStep("PREVIEW", "当前赛制为 " + formatLabel(format)
                + "，共 " + players.size() + " 名参赛选手，将生成 " + r.getMatchCount() + " 场比赛"
                + ("GROUP_KNOCKOUT".equals(format) ? "（" + effectiveGroups + " 个小组，每组出线 " + advance + " 人）" : ""));
        return r;
    }

    // ========================================================================
    //  正式执行
    // ========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrawOutcomeVo execute(String tournamentId, DrawConfigDto config) {
        Tournament t = requireTournament(tournamentId);
        if ("FINISHED".equals(t.getStatus())) {
            throw new BizException(40002, "赛事已结束，如需重排请先撤销抽签");
        }
        if (config == null) config = new DrawConfigDto();

        List<Player> players = resolvePlayers(t, config);
        if (players.size() < 2) {
            throw new BizException(40002, "参赛选手不足 2 人，无法编排");
        }

        // 同步配置到赛事
        applyConfig(t, config);

        // 清空历史编排
        matchService.deleteByTournament(tournamentId);
        clearGroups(tournamentId);
        clearLogs(tournamentId);

        String format = effectiveFormat(t, config);
        String prefix = IdGen.matchPrefix(tournamentId);
        int participants = players.size();

        DrawOutcomeVo vo = new DrawOutcomeVo();
        vo.setTournament_id(tournamentId);
        vo.setFormat(format);
        vo.setFormat_label(formatLabel(format));
        vo.setParticipants(participants);

        List<Match> matches;
        // 逐轮配对签表中轮空也是一条场次记录，但不属于「真实对局」，单独统计
        int byeMatches = 0;

        if ("SINGLE_ELIM".equals(format)) {
            DrawResult res = engine.draw(buildRequest(players, config, t));
            boolean third = config.getThirdPlace() == null || Boolean.TRUE.equals(config.getThirdPlace());

            // 抽签引擎负责「种子落位 + 同单位分散」，其输出是 2^n 签位表；
            // 这里剔除其中的轮空项，得到纯选手的落位顺序，作为逐轮配对的首轮配对顺序。
            List<String> order = new ArrayList<>();
            for (String id : res.getDrawPositions()) {
                if (id != null && !DrawEngine.BYE.equals(id)) order.add(id);
            }
            boolean avoid = config.getAvoidSameTeam() != null
                    ? Boolean.TRUE.equals(config.getAvoidSameTeam())
                    : (t.getAvoid_same_team() == null || Boolean.TRUE.equals(t.getAvoid_same_team()));
            // 用与抽签引擎一致的视图（含赛事级种子裁剪），保证"可换位选手"判断一致
            List<DrawRequest.PlayerView> views = toViewsFor(players, t);
            int fixed = engine.repairFirstRoundClashes(order, views, avoid);

            matches = BracketBuilder.buildDynamic(tournamentId, prefix + "K", order,
                    BracketBuilder.STAGE_KNOCKOUT, third);

            int byes = countByes(matches);
            int rounds = BracketBuilder.roundCountOf(order.size());
            FairnessReport fr = engine.analyzeOrder(order, views);
            boolean firstRoundBye = order.size() % 2 == 1;

            List<com.ape.badminton.algorithm.DrawStep> steps = new ArrayList<>();
            steps.add(new com.ape.badminton.algorithm.DrawStep(1, "FILL",
                    "逐轮配对签表：" + order.size() + " 人参赛，共 " + rounds + " 轮、"
                            + (matches.size() - byes) + " 场对局。每轮两两配对，人数为奇数时 1 人轮空；"
                            + "本轮签表首轮" + (firstRoundBye ? "有 1 人轮空" : "没有轮空")
                            + (byes > 0 ? "，全程共 " + byes + " 人次轮空" : ""),
                    String.join("|", order)));
            steps.add(new com.ape.badminton.algorithm.DrawStep(2, "SEED",
                    "种子蛇形落位：" + (t.getSeed_count() == null ? 0 : t.getSeed_count())
                            + " 名种子分处签表两端，最早只能在决赛相遇", null));
            steps.add(new com.ape.badminton.algorithm.DrawStep(3, "TEAM",
                    avoid
                            ? (fixed > 0
                                    ? "同单位规避：执行 " + fixed + " 次换位修正，首轮已无同单位对阵"
                                    : "同单位规避：首轮无同单位对阵，无需修正")
                            : "未启用同单位规避，首轮可能出现同单位对阵", null));
            steps.add(new com.ape.badminton.algorithm.DrawStep(4, "VERIFY",
                    "校验通过：首轮死签 " + fr.getFirstRoundClashes() + " 对，综合公平性 "
                            + fr.getScore() + " 分", null));

            vo.setBracket_size(order.size());
            vo.setBye_count(byes);
            vo.setRounds(rounds);
            vo.setDraw_positions(order);
            vo.setSteps(steps);
            vo.setFairness(fr);
            vo.setAvoidance_resolved(fr.getFirstRoundClashes() == 0);
            vo.setSlots(buildOrderSlots(order, players));

            byeMatches = byes;

            saveLogs(tournamentId, steps);
            t.setBracket_size(order.size());
            t.setBye_count(byes);

        } else if ("ROUND_ROBIN".equals(format)) {
            List<String> ids = idsOf(players);
            matches = RoundRobinEngine.build(tournamentId, prefix + "L", ids,
                    RoundRobinEngine.STAGE_ROUND_ROBIN, 0, 1);
            vo.setBracket_size(0);
            vo.setBye_count(0);
            vo.setRounds(matches.isEmpty() ? 0 : matches.get(matches.size() - 1).getRound());
            vo.setSlots(new ArrayList<>());
            // 循环赛的公平性体现为「赛程均衡」，直接给出说明性留痕
            List<com.ape.badminton.algorithm.DrawStep> steps = new ArrayList<>();
            steps.add(new com.ape.badminton.algorithm.DrawStep(1, "ROUND_ROBIN",
                    "采用轮转法（Berger tables）生成单循环赛程：" + participants + " 人共 "
                            + vo.getRounds() + " 轮、" + matches.size() + " 场，任意两人恰好相遇一次", null));
            vo.setSteps(steps);
            saveLogs(tournamentId, steps);
            t.setBracket_size(0);
            t.setBye_count(0);

        } else { // GROUP_KNOCKOUT
            int gc = GroupKnockoutEngine.suggestGroupCount(participants,
                    config.getGroupCount() == null ? 0 : config.getGroupCount());
            List<DrawRequest.PlayerView> views = toViews(players);
            List<List<DrawRequest.PlayerView>> groups = GroupKnockoutEngine.snakeAssign(views, gc,
                    config.getRandomSeed());
            saveGroups(tournamentId, groups);

            // 分组公平性度量（实力均衡度 / 种子覆盖度 / 同单位规避）
            FairnessReport groupFairness = GroupKnockoutEngine.analyzeGroups(groups);
            vo.setFairness(groupFairness);
            vo.setAvoidance_resolved(groupFairness.getSameTeamInGroup() == 0);

            matches = new ArrayList<>();
            for (int gi = 0; gi < groups.size(); gi++) {
                List<String> ids = new ArrayList<>();
                for (DrawRequest.PlayerView p : groups.get(gi)) ids.add(p.getPlayerId());
                matches.addAll(RoundRobinEngine.build(tournamentId, prefix + "g" + (gi + 1), ids,
                        RoundRobinEngine.STAGE_GROUP, gi + 1, 1));
            }

            vo.setGroup_count(gc);
            vo.setBracket_size(0);
            vo.setBye_count(0);
            int rounds = 0;
            for (Match m : matches) {
                if (m.getRound() != null) rounds = Math.max(rounds, m.getRound());
            }
            vo.setRounds(rounds);

            List<com.ape.badminton.algorithm.DrawStep> steps = new ArrayList<>();
            int seq = 1;
            for (int gi = 0; gi < groups.size(); gi++) {
                List<String> names = new ArrayList<>();
                for (DrawRequest.PlayerView p : groups.get(gi)) names.add(p.getName());
                steps.add(new com.ape.badminton.algorithm.DrawStep(seq++, "GROUP",
                        "蛇形分组：第 " + GroupKnockoutEngine.groupName(gi + 1) + " 组共 "
                                + groups.get(gi).size() + " 人 —— " + String.join("、", names), null));
            }
            steps.add(new com.ape.badminton.algorithm.DrawStep(seq, "GROUP_LOCK",
                    "共 " + groups.size() + " 组，每组前 " + t.getGroup_advance()
                            + " 名出线，进入交叉淘汰赛（A1-B2、B1-A2 交叉对阵）", null));
            vo.setSteps(steps);
            saveLogs(tournamentId, steps);
            t.setGroup_count(gc);
            t.setBracket_size(0);
            t.setBye_count(0);
        }

        matchService.saveAll(matches);
        // 「场次」按真实对局数统计，轮空场次单独通过 bye_count 展示，
        // 这样与配置页预览的「预计场次」口径一致
        vo.setMatch_count(matches.size() - byeMatches);

        t.setActual_participants(participants);
        t.setStatus("DRAWN");
        tournamentService.update(t);

        return vo;
    }

    // ========================================================================
    //  小组出线 → 交叉淘汰
    // ========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateKnockout(String tournamentId) {
        Tournament t = requireTournament(tournamentId);
        if (!"GROUP_KNOCKOUT".equals(t.getFormat())) {
            throw new BizException(40002, "当前赛事不是「小组循环 + 淘汰」赛制");
        }
        List<Map<String, Object>> groups = statsService.groupStandings(tournamentId);
        if (groups.isEmpty()) throw new BizException(40002, "尚未分组，请先执行抽签编排");

        for (Map<String, Object> g : groups) {
            if (!Boolean.TRUE.equals(g.get("finished"))) {
                throw new BizException(40002, "小组「" + g.get("group_name") + "」仍有未完成场次，无法生成淘汰赛");
            }
        }

        int advance = t.getGroup_advance() == null ? 2 : t.getGroup_advance();
        List<List<String>> qualified = new ArrayList<>();
        for (Map<String, Object> g : groups) {
            Object o = g.get("standings");
            List<String> ids = new ArrayList<>();
            if (o instanceof List) {
                for (Object item : (List<?>) o) {
                    if (item instanceof Standing && ids.size() < advance) {
                        ids.add(((Standing) item).getPlayerId());
                    }
                }
            }
            qualified.add(ids);
        }

        int qualifierCount = 0;
        for (List<String> q : qualified) qualifierCount += q.size();
        if (qualifierCount < 2) throw new BizException(40002, "出线人数不足 2 人");

        // ---------- 首轮配对顺序 ----------
        // 出线人数恰好是 2 的幂时，签位正好填满，沿用既有的「A1-D2、C1-B2…」
        // 蛇形交叉结构（同组两人最早只能在决赛相遇）。
        //
        // 人数不是 2 的幂时，2^n 签位会撑出一堆轮空 —— 10 人出线会变成 16 签位、
        // 首轮 8 个配对里只有 2 场真打。因此改用「名次层交错」顺序：
        // 先排各组第 1 名，再排各组第 2 名……相邻配对天然跨组，
        // 10 人就是实打实的 5 场；人数为奇数时轮空 1 人。
        int kSize = DrawEngine.bracketSize(qualifierCount);
        List<String> order = new ArrayList<>();
        if (kSize == qualifierCount) {
            for (String p : GroupKnockoutEngine.crossKnockoutPositions(qualified, advance, kSize)) {
                if (p != null && !DrawEngine.BYE.equals(p)) order.add(p);
            }
        } else {
            int maxRank = 0;
            for (List<String> grp : qualified) maxRank = Math.max(maxRank, grp.size());
            for (int rank = 0; rank < maxRank; rank++) {
                for (List<String> grp : qualified) {
                    if (rank < grp.size()) order.add(grp.get(rank));
                }
            }
        }

        // 首轮冲突修正：同组两人首轮相遇是硬约束（小组赛白打），
        // 同单位则按赛事的「同单位规避」开关处理。
        List<Player> players = entryService.players(tournamentId);
        Map<String, String> teamOf = new HashMap<>();
        for (Player p : players) teamOf.put(p.getPlayer_id(), p.getTeam());
        Map<String, String> groupOf = new HashMap<>();
        for (int gi = 0; gi < qualified.size(); gi++) {
            for (String pid : qualified.get(gi)) groupOf.put(pid, String.valueOf(gi));
        }
        boolean avoid = t.getAvoid_same_team() == null || Boolean.TRUE.equals(t.getAvoid_same_team());
        int repairs = GroupKnockoutEngine.repairOrderClashes(order, teamOf, groupOf, avoid);

        matchService.deleteByTournamentAndStage(tournamentId, BracketBuilder.STAGE_KNOCKOUT);
        matchService.deleteByTournamentAndStage(tournamentId, BracketBuilder.STAGE_THIRD);

        String prefix = IdGen.matchPrefix(tournamentId);
        List<Match> knockout = BracketBuilder.buildDynamic(tournamentId, prefix + "K",
                order, BracketBuilder.STAGE_KNOCKOUT, true);
        matchService.saveAll(knockout);

        t.setStatus("ONGOING");
        tournamentService.update(t);

        int rounds = BracketBuilder.roundCountOf(order.size());
        List<com.ape.badminton.algorithm.DrawStep> steps = new ArrayList<>();
        steps.add(new com.ape.badminton.algorithm.DrawStep(1, "CROSS",
                "小组赛全部结束，共 " + qualifierCount + " 人出线：采用逐轮配对交叉淘汰，首轮 "
                        + (order.size() / 2) + " 场"
                        + (order.size() % 2 == 1 ? "（1 人轮空）" : "（无轮空）")
                        + "，共 " + rounds + " 轮", null));
        if (repairs > 0) {
            steps.add(new com.ape.badminton.algorithm.DrawStep(2, "REPAIR",
                    "淘汰赛首轮冲突检测：执行 " + repairs + " 次换位（同组 / 同单位规避）", null));
        }
        saveLogs(tournamentId, steps);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("qualifiers", qualifierCount);
        out.put("bracket_size", order.size());
        out.put("match_count", knockout.size());
        out.put("repairs", repairs);
        return out;
    }

    // ========================================================================
    //  查询与撤销
    // ========================================================================

    @Override
    public List<DrawLog> logs(String tournamentId) {
        QueryWrapper<DrawLog> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId).orderByAsc("seq");
        return drawLogMapper.selectList(w);
    }

    @Override
    public FairnessReport fairness(String tournamentId) {
        Tournament t = tournamentService.getById(tournamentId);
        List<Player> players = entryService.players(tournamentId);

        // 分组赛制：直接从已落库的分组结构做均衡度分析
        if (t != null && "GROUP_KNOCKOUT".equals(t.getFormat())) {
            List<List<DrawRequest.PlayerView>> groups = loadGroups(tournamentId, players, t);
            if (!groups.isEmpty()) {
                return GroupKnockoutEngine.analyzeGroups(groups);
            }
        }

        // 淘汰赛 / 循环赛：从抽签留痕快照还原签位表
        List<DrawLog> logs = logs(tournamentId);
        String snapshot = null;
        for (int i = logs.size() - 1; i >= 0; i--) {
            if (logs.get(i).getSnapshot() != null && !logs.get(i).getSnapshot().isEmpty()) {
                snapshot = logs.get(i).getSnapshot();
                break;
            }
        }
        if (snapshot == null) return null;
        List<String> positions = new ArrayList<>();
        for (String s : snapshot.split("\\|", -1)) {
            positions.add("-".equals(s) ? null : s);
        }
        // 单淘汰已改为「逐轮配对」签表：留痕快照存的是首轮配对顺序，
        // 用顺序维度的分析方法；其余赛制仍是 2^n 签位，沿用原分析。
        if (t != null && "SINGLE_ELIM".equals(t.getFormat())) {
            return engine.analyzeOrder(positions, toViewsFor(players, t));
        }
        return engine.analyzePositions(positions, toViewsFor(players, t));
    }

    /** 从 biz_local_groups 还原分组结构（按组号、组内顺位排序） */
    private List<List<DrawRequest.PlayerView>> loadGroups(String tournamentId,
                                                          List<Player> players, Tournament t) {
        QueryWrapper<GroupEntry> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId).orderByAsc("group_no").orderByAsc("seed_position");
        List<GroupEntry> entries = groupMapper.selectList(w);
        Map<String, DrawRequest.PlayerView> viewMap = new HashMap<>();
        for (DrawRequest.PlayerView v : toViewsFor(players, t)) viewMap.put(v.getPlayerId(), v);

        Map<Integer, List<DrawRequest.PlayerView>> byGroup = new TreeMap<>();
        for (GroupEntry e : entries) {
            DrawRequest.PlayerView v = viewMap.get(e.getPlayer_id());
            if (v == null) continue;
            byGroup.computeIfAbsent(e.getGroup_no(), k -> new ArrayList<>()).add(v);
        }
        return new ArrayList<>(byGroup.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reset(String tournamentId) {
        Tournament t = requireTournament(tournamentId);
        matchService.deleteByTournament(tournamentId);
        clearGroups(tournamentId);
        clearLogs(tournamentId);
        t.setStatus("READY");
        t.setBracket_size(0);
        t.setBye_count(0);
        t.setGroup_count(0);
        t.setChampion_id(null);
        t.setRunner_up_id(null);
        t.setThird_id(null);
        return tournamentService.update(t);
    }

    // ========================================================================
    //  内部辅助
    // ========================================================================

    private Tournament requireTournament(String tournamentId) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) throw new BizException(40400, "赛事不存在");
        return t;
    }

    private String effectiveFormat(Tournament t, DrawConfigDto config) {
        if (config != null && config.getGroupCount() != null && config.getGroupCount() > 0
                && "GROUP_KNOCKOUT".equals(t.getFormat())) {
            return "GROUP_KNOCKOUT";
        }
        return t.getFormat() == null ? "SINGLE_ELIM" : t.getFormat();
    }

    /**
     * 把本次编排的配置写回赛事。
     *
     * 每个字段都只在「本次显式指定」时才覆盖（DTO 未指定为 null），
     * 绝不能拿调用方没传的默认值去冲掉赛事原有配置 —— 见 DrawConfigDto 的注释。
     */
    private void applyConfig(Tournament t, DrawConfigDto c) {
        if (c.getSeedCount() != null) t.setSeed_count(c.getSeedCount());
        if (c.getAvoidSameTeam() != null) t.setAvoid_same_team(c.getAvoidSameTeam());
        // 五局三胜已下线：只接受 1 局定胜负 / 3 局两胜，其余值忽略（保留赛事原配置）
        if (c.getBestOf() != null && (c.getBestOf() == 1 || c.getBestOf() == 3)) {
            t.setBest_of(c.getBestOf());
        }
        if (c.getPointsPerGame() != null && c.getPointsPerGame() > 0) t.setPoints_per_game(c.getPointsPerGame());
        if (c.getPointsCap() != null && c.getPointsCap() > 0) t.setPoints_cap(c.getPointsCap());
        if (c.getGroupAdvance() != null && c.getGroupAdvance() > 0) t.setGroup_advance(c.getGroupAdvance());
        if ("GROUP_KNOCKOUT".equals(t.getFormat())) {
            if (c.getGroupCount() != null && c.getGroupCount() > 0) t.setGroup_count(c.getGroupCount());
        }
    }

    private List<Player> resolvePlayers(Tournament t, DrawConfigDto config) {
        if (config != null && config.getPlayerIds() != null && !config.getPlayerIds().isEmpty()) {
            List<Player> list = new ArrayList<>();
            for (String id : config.getPlayerIds()) {
                Player p = playerMapper.selectById(id);
                if (p != null) list.add(p);
            }
            return list;
        }
        return entryService.players(t.getTournament_id());
    }

    private DrawRequest buildRequest(List<Player> players, DrawConfigDto config, Tournament t) {
        DrawRequest req = new DrawRequest();
        req.setPlayers(toViewsFor(players, t));
        boolean avoid = config != null && config.getAvoidSameTeam() != null
                ? Boolean.TRUE.equals(config.getAvoidSameTeam())
                : (t.getAvoid_same_team() == null || Boolean.TRUE.equals(t.getAvoid_same_team()));
        req.setAvoidSameTeam(avoid);
        req.setRandomSeed(config == null ? null : config.getRandomSeed());
        return req;
    }

    /** 把选手实体转为算法层视图；种子信息来自赛事级报名（已由服务层写入 Player） */
    private List<DrawRequest.PlayerView> toViews(List<Player> players) {
        List<DrawRequest.PlayerView> views = new ArrayList<>();
        for (Player p : players) {
            views.add(new DrawRequest.PlayerView(p.getPlayer_id(), p.getName(), p.getTeam(),
                    p.getLevel() == null ? 0 : p.getLevel(),
                    Boolean.TRUE.equals(p.getIs_seed()),
                    p.getSeed_rank() == null ? 0 : p.getSeed_rank()));
        }
        return views;
    }

    /**
     * 按赛事配置裁剪种子数量：只保留报名时标记为种子、且顺位在前 N 的选手
     */
    private List<DrawRequest.PlayerView> toViewsFor(List<Player> players, Tournament t) {
        List<DrawRequest.PlayerView> views = toViews(players);
        int seedCount = t == null || t.getSeed_count() == null ? 4 : t.getSeed_count();
        if (seedCount <= 0) {
            for (DrawRequest.PlayerView v : views) {
                v.setSeed(false);
                v.setSeedRank(0);
            }
            return views;
        }
        List<DrawRequest.PlayerView> seeds = new ArrayList<>();
        for (DrawRequest.PlayerView v : views) {
            if (v.isSeed()) seeds.add(v);
        }
        seeds.sort((a, b) -> Integer.compare(a.getSeedRank(), b.getSeedRank()));
        for (int i = 0; i < seeds.size(); i++) {
            boolean isSeed = i < seedCount;
            seeds.get(i).setSeed(isSeed);
            if (isSeed) {
                seeds.get(i).setSeedRank(i + 1);
            } else {
                seeds.get(i).setSeedRank(0);
            }
        }
        return views;
    }

    private List<String> idsOf(List<Player> players) {
        List<String> ids = new ArrayList<>();
        for (Player p : players) ids.add(p.getPlayer_id());
        return ids;
    }

    /**
     * 逐轮配对签表的「首轮顺序表」：相邻两位即首轮对手。
     * 与旧版签位表的区别是不再填充轮空占位，因此长度等于实际参赛人数。
     */
    private List<DrawOutcomeVo.SeedSlot> buildOrderSlots(List<String> order, List<Player> players) {
        Map<String, Player> map = new HashMap<>();
        for (Player p : players) map.put(p.getPlayer_id(), p);
        List<DrawOutcomeVo.SeedSlot> slots = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String id = order.get(i);
            DrawOutcomeVo.SeedSlot s = new DrawOutcomeVo.SeedSlot();
            s.setSlot(i + 1);
            s.setPlayer_id(id);
            Player p = map.get(id);
            if (p != null) {
                s.setPlayer_name(p.getName());
                s.setTeam(p.getTeam());
                s.setSeed(Boolean.TRUE.equals(p.getIs_seed()));
                s.setSeed_rank(p.getSeed_rank() == null ? 0 : p.getSeed_rank());
            }
            slots.add(s);
        }
        return slots;
    }

    /**
     * 估算一场赛事的预计总场次数
     *
     * @param format     赛制
     * @param n          参赛人数
     * @param groupCount 期望小组数（0 表示自动）
     * @param advance    每组出线人数
     * @param third      是否设置季军赛
     */
    private int estimateMatchCount(String format, int n, int groupCount, int advance, boolean third) {
        if ("ROUND_ROBIN".equals(format)) {
            return n * (n - 1) / 2;
        }
        if ("GROUP_KNOCKOUT".equals(format)) {
            int gc = GroupKnockoutEngine.suggestGroupCount(n, groupCount);
            int base = n / gc;
            int rem = n % gc;
            int groupsMatches = 0;
            for (int i = 0; i < gc; i++) {
                int sz = base + (i < rem ? 1 : 0);
                groupsMatches += sz * (sz - 1) / 2;
            }
            // 出线后的交叉淘汰同样是逐轮配对：场次恒为「出线人数 - 1」，
            // 与签位规模（2 的幂）无关 —— 10 人出线就是 9 场，而不是 16 签位的 15 场
            int qualifiers = gc * advance;
            int koMatches = Math.max(0, qualifiers - 1)
                    + (third && BracketBuilder.dynamicThirdPlacePossible(qualifiers) ? 1 : 0);
            return groupsMatches + koMatches;
        }
        // 单淘汰逐轮配对：每场淘汰 1 人，真实对局数恒为 n-1；季军赛另计
        return Math.max(0, n - 1)
                + (third && BracketBuilder.dynamicThirdPlacePossible(n) ? 1 : 0);
    }

    /**
     * 统计对阵树中的轮空人次。
     *
     * 注意：不能只看 status=BYE。逐轮配对下，轮空名额可能落在「上一轮的胜者」上，
     * 这类场次在编排当刻还不知道具体选手，状态是 PENDING、选手位为空，
     * 必须靠 BYE 标记来识别（等上一轮打完会自动晋级）。
     */
    private int countByes(List<Match> matches) {
        int n = 0;
        for (Match m : matches) {
            if (BracketBuilder.STATUS_BYE.equals(m.getStatus())
                    || DrawEngine.BYE.equals(m.getPlayer_a_id())
                    || DrawEngine.BYE.equals(m.getPlayer_b_id())) {
                n++;
            }
        }
        return n;
    }

    private void saveLogs(String tournamentId, List<com.ape.badminton.algorithm.DrawStep> steps) {
        if (steps == null) return;
        for (com.ape.badminton.algorithm.DrawStep s : steps) {
            DrawLog log = new DrawLog();
            log.setLog_id(IdGen.next("dl"));
            log.setTournament_id(tournamentId);
            log.setSeq(s.getSeq());
            log.setStep(s.getStep());
            log.setMessage(s.getMessage() == null || s.getMessage().length() > 480
                    ? String.valueOf(s.getMessage()).substring(0, Math.min(480, s.getMessage().length()))
                    : s.getMessage());
            log.setSnapshot(s.getSnapshot());
            log.setCreate_time(new Date());
            drawLogMapper.insert(log);
        }
    }

    private void clearLogs(String tournamentId) {
        QueryWrapper<DrawLog> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId);
        drawLogMapper.delete(w);
    }

    private void clearGroups(String tournamentId) {
        QueryWrapper<GroupEntry> w = new QueryWrapper<>();
        w.eq("tournament_id", tournamentId);
        groupMapper.delete(w);
    }

    private void saveGroups(String tournamentId, List<List<DrawRequest.PlayerView>> groups) {
        for (int gi = 0; gi < groups.size(); gi++) {
            List<DrawRequest.PlayerView> g = groups.get(gi);
            for (int i = 0; i < g.size(); i++) {
                GroupEntry e = new GroupEntry();
                e.setGroup_id(IdGen.next("g"));
                e.setTournament_id(tournamentId);
                e.setGroup_no(gi + 1);
                e.setPlayer_id(g.get(i).getPlayerId());
                e.setSeed_position(i + 1);
                groupMapper.insert(e);
            }
        }
    }

    private String formatLabel(String format) {
        switch (format == null ? "" : format) {
            case "SINGLE_ELIM": return "单淘汰赛";
            case "ROUND_ROBIN": return "单循环赛";
            case "GROUP_KNOCKOUT": return "小组循环 + 交叉淘汰";
            default: return format;
        }
    }
}
