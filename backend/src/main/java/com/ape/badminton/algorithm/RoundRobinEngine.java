package com.ape.badminton.algorithm;

import com.ape.badminton.entity.Match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单循环赛编排与积分排名引擎
 *
 * 赛程生成采用经典的「轮转法（Circle / Berger tables）」：
 *   - 参赛人数为奇数时补一个虚拟轮空位，保证每轮都有人轮空
 *   - 每轮固定首个位置，其余位置顺时针轮转，从而保证任意两名选手恰好相遇一次
 *
 * 排名规则（依次比较，与羽毛球竞赛惯例一致）：
 *   积分(胜场) → 净胜局 → 净胜分 → 相互胜负关系 → 抽签序
 */
public class RoundRobinEngine {

    public static final String STAGE_ROUND_ROBIN = "ROUND_ROBIN";
    public static final String STAGE_GROUP = "GROUP";

    /**
     * 生成单循环赛程
     *
     * @param tournamentId 赛事ID
     * @param idPrefix     场次ID前缀
     * @param playerIds    参赛选手
     * @param stage        阶段标识（ROUND_ROBIN 或 GROUP）
     * @param groupNo      小组序号，0 表示非小组
     * @param startRound   起始轮次（小组赛各组依次排开时使用）
     */
    public static List<Match> build(String tournamentId, String idPrefix, List<String> playerIds,
                                    String stage, int groupNo, int startRound) {
        List<Match> matches = new ArrayList<>();
        if (playerIds == null || playerIds.size() < 2) return matches;

        List<String> arr = new ArrayList<>(playerIds);
        if (arr.size() % 2 != 0) arr.add(DrawEngine.BYE);

        int n = arr.size();
        int totalRounds = n - 1;
        String prefix = safePrefix(idPrefix);

        for (int r = 0; r < totalRounds; r++) {
            int order = 1;
            for (int i = 0; i < n / 2; i++) {
                String a = arr.get(i);
                String b = arr.get(n - 1 - i);
                if (DrawEngine.BYE.equals(a) || DrawEngine.BYE.equals(b)) continue;
                // 主客交替：奇数轮交换对阵双方，让赛程更均匀
                if (r % 2 == 1) {
                    String t = a;
                    a = b;
                    b = t;
                }
                Match m = new Match();
                m.setMatch_id(prefix + "_R" + (startRound + r) + "_" + (order++));
                m.setTournament_id(tournamentId);
                m.setStage(stage);
                m.setRound(startRound + r);
                m.setRound_name(groupNo > 0 ? ("第 " + (startRound + r) + " 轮") : ("第 " + (startRound + r) + " 轮"));
                m.setGroup_no(groupNo);
                m.setMatch_order(order - 1);
                m.setPlayer_a_id(a);
                m.setPlayer_b_id(b);
                m.setStatus(BracketBuilder.STATUS_READY);
                m.setScore_a(0);
                m.setScore_b(0);
                m.setGames_a(0);
                m.setGames_b(0);
                matches.add(m);
            }
            // 轮转：固定 arr[0]，其余向右旋转一格
            String last = arr.remove(n - 1);
            arr.add(1, last);
        }
        return matches;
    }

    /**
     * 计算循环赛积分榜
     *
     * @param playerIds 参赛者（决定榜单包含哪些人，即使一场未赛也列出）
     * @param matches   已完成/进行中的场次
     * @param names     playerId → 姓名
     * @param teams     playerId → 所属单位
     */
    public static List<Standing> standings(List<String> playerIds, List<Match> matches,
                                           Map<String, String> names, Map<String, String> teams) {
        Map<String, Standing> map = new LinkedHashMap<>();
        for (String id : playerIds) {
            Standing s = new Standing();
            s.setPlayerId(id);
            s.setPlayerName(names == null ? id : names.getOrDefault(id, id));
            s.setTeam(teams == null ? "" : teams.getOrDefault(id, ""));
            map.put(id, s);
        }

        for (Match m : matches) {
            if (!BracketBuilder.STATUS_DONE.equals(m.getStatus())) continue;
            String a = m.getPlayer_a_id();
            String b = m.getPlayer_b_id();
            if (a == null || b == null) continue;
            Standing sa = map.get(a);
            Standing sb = map.get(b);
            if (sa == null || sb == null) continue;

            sa.setPlayed(sa.getPlayed() + 1);
            sb.setPlayed(sb.getPlayed() + 1);

            int ga = nz(m.getGames_a()), gb = nz(m.getGames_b());
            int pa = nz(m.getScore_a()), pb = nz(m.getScore_b());
            // 若未录入局分，则用总分兜底估算
            if (ga == 0 && gb == 0) {
                if (pa > pb) { ga = 1; gb = 0; }
                else if (pb > pa) { ga = 0; gb = 1; }
            }
            sa.setGamesWon(sa.getGamesWon() + ga);
            sa.setGamesLost(sa.getGamesLost() + gb);
            sb.setGamesWon(sb.getGamesWon() + gb);
            sb.setGamesLost(sb.getGamesLost() + ga);
            sa.setPointsWon(sa.getPointsWon() + pa);
            sa.setPointsLost(sa.getPointsLost() + pb);
            sb.setPointsWon(sb.getPointsWon() + pb);
            sb.setPointsLost(sb.getPointsLost() + pa);

            if (m.getWinner_id() != null && m.getWinner_id().equals(a)) {
                sa.setWon(sa.getWon() + 1);
                sa.setPoints(sa.getPoints() + 2);
                sb.setLost(sb.getLost() + 1);
            } else if (m.getWinner_id() != null && m.getWinner_id().equals(b)) {
                sb.setWon(sb.getWon() + 1);
                sb.setPoints(sb.getPoints() + 2);
                sa.setLost(sa.getLost() + 1);
            }
        }

        List<Standing> list = new ArrayList<>(map.values());
        // 相互胜负关系缓存：用于并列时的 head-to-head 比较
        Map<String, Integer> h2h = new HashMap<>();
        for (Match m : matches) {
            if (!BracketBuilder.STATUS_DONE.equals(m.getStatus())) continue;
            if (m.getWinner_id() == null) continue;
            String loser = m.getWinner_id().equals(m.getPlayer_a_id()) ? m.getPlayer_b_id() : m.getPlayer_a_id();
            if (loser == null) continue;
            h2h.merge(m.getWinner_id() + ">" + loser, 1, Integer::sum);
        }

        list.sort((x, y) -> {
            if (y.getPoints() != x.getPoints()) return y.getPoints() - x.getPoints();
            if (y.getGameDiff() != x.getGameDiff()) return y.getGameDiff() - x.getGameDiff();
            if (y.getPointDiff() != x.getPointDiff()) return y.getPointDiff() - x.getPointDiff();
            int hx = h2h.getOrDefault(x.getPlayerId() + ">" + y.getPlayerId(), 0);
            int hy = h2h.getOrDefault(y.getPlayerId() + ">" + x.getPlayerId(), 0);
            if (hx != hy) return hy - hx;
            return String.valueOf(x.getPlayerId()).compareTo(String.valueOf(y.getPlayerId()));
        });

        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String safePrefix(String idPrefix) {
        String p = idPrefix == null ? "m" : idPrefix.replaceAll("[^A-Za-z0-9_]", "");
        return p.length() > 24 ? p.substring(0, 24) : p;
    }
}
