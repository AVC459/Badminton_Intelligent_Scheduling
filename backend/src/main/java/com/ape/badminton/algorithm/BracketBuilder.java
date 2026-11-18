package com.ape.badminton.algorithm;

import com.ape.badminton.entity.Match;

import java.util.ArrayList;
import java.util.List;

/**
 * 对阵树生成器
 *
 * 把抽签得到的签位表展开为完整的淘汰赛对阵树，并建立「胜方晋级」的指针关系。
 * 同时支持季军赛（半决赛负方自动进入季军赛）。
 */
public class BracketBuilder {

    public static final String STAGE_KNOCKOUT = "KNOCKOUT";
    public static final String STAGE_THIRD = "THIRD_PLACE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_BYE = "BYE";
    public static final String STATUS_DONE = "DONE";

    /** 轮次名称 */
    public static String roundName(int round, int rounds) {
        int fromEnd = rounds - round;
        switch (fromEnd) {
            case 0: return "决赛";
            case 1: return "半决赛";
            case 2: return "1/4决赛";
            case 3: return "1/8决赛";
            case 4: return "1/16决赛";
            case 5: return "1/32决赛";
            default: return "第" + round + "轮";
        }
    }

    /**
     * 由签位表构建淘汰赛对阵树
     *
     * @param tournamentId 赛事ID
     * @param idPrefix     场次ID前缀（用于区分阶段，如 t1 或 t1_G）
     * @param positions    签位表，长度为 2^n；元素为 playerId / "BYE" / null
     * @param stage        阶段标识
     * @param withThirdPlace 是否生成季军赛
     */
    public static List<Match> buildKnockout(String tournamentId, String idPrefix, List<String> positions,
                                            String stage, boolean withThirdPlace) {
        List<Match> all = new ArrayList<>();
        int size = positions.size();
        int rounds = DrawEngine.roundsOf(size);
        if (rounds == 0) return all;

        String prefix = safePrefix(idPrefix);

        // ---------- 首轮 ----------
        List<Match> current = new ArrayList<>();
        for (int i = 0, order = 1; i < size; i += 2, order++) {
            Match m = newMatch(tournamentId, prefix, stage, 1, rounds, order, 0);
            m.setPlayer_a_id(positions.get(i));
            m.setPlayer_b_id(positions.get(i + 1));
            all.add(m);
            current.add(m);
        }

        // ---------- 后续轮次 ----------
        for (int round = 2; round <= rounds; round++) {
            List<Match> next = new ArrayList<>();
            for (int i = 0, order = 1; i < current.size(); i += 2, order++) {
                Match m = newMatch(tournamentId, prefix, stage, round, rounds, order, 0);
                all.add(m);
                next.add(m);
                // 上一轮相邻两场胜者晋级到本场，分别占据 A / B 位
                current.get(i).setNext_match_id(m.getMatch_id());
                current.get(i).setNext_slot("A");
                if (i + 1 < current.size()) {
                    current.get(i + 1).setNext_match_id(m.getMatch_id());
                    current.get(i + 1).setNext_slot("B");
                }
            }
            current = next;
        }

        // ---------- 季军赛 ----------
        if (withThirdPlace && rounds >= 2) {
            List<Match> semis = new ArrayList<>();
            for (Match m : all) {
                if (STAGE_KNOCKOUT.equals(m.getStage()) && m.getRound() == rounds - 1) {
                    semis.add(m);
                }
            }
            if (semis.size() >= 2) {
                Match third = newMatch(tournamentId, prefix, STAGE_THIRD, rounds, rounds, 1, 0);
                // 季军赛与决赛同轮次、同 order，必须改用独立后缀，
                // 否则场次 ID 会与决赛（prefix_R{rounds}_1）冲突导致主键重复插入失败
                third.setMatch_id(prefix + "_THIRD");
                third.setRound_name("季军赛");
                all.add(third);
                semis.get(0).setLoser_next_id(third.getMatch_id());
                semis.get(0).setLoser_slot("A");
                semis.get(1).setLoser_next_id(third.getMatch_id());
                semis.get(1).setLoser_slot("B");
            }
        }

        // ---------- 轮空自动晋级 ----------
        resolveByes(all);
        refreshStatus(all);
        return all;
    }

    // ========================================================================
    //  逐轮配对（动态签表）
    // ========================================================================

    /** 逐轮配对的槽位：要么是已确定选手，要么是待决出的上一轮场次 */
    private static class Slot {
        String playerId;
        Match match;

        static Slot ofPlayer(String id) {
            Slot s = new Slot();
            s.playerId = id;
            return s;
        }

        static Slot ofMatch(Match m) {
            Slot s = new Slot();
            s.match = m;
            return s;
        }
    }

    /**
     * 动态签表的轮次名称。
     *
     * 与 {@link #roundName} 口径一致：按「距决赛的轮数」命名，
     * 于是 10 人参赛时首轮（5 场）就是「1/8决赛」，20 人时首轮是「1/16决赛」、
     * 次轮 5 场即「1/8决赛」—— 与 2^n 签位下的名称完全对得上。
     */
    public static String roundNameDynamic(int round, int rounds) {
        return roundName(round, rounds);
    }

    /** 逐轮配对所需的轮数：每轮人数折半向上取整 */
    public static int roundCountOf(int n) {
        int r = 0;
        while (n > 1) {
            n = (n + 1) / 2;
            r++;
        }
        return r;
    }

    /** 逐轮配对下的轮空人次总数：每轮人数为奇数即产生 1 人次轮空 */
    public static int dynamicByeCount(int n) {
        int byes = 0;
        int cur = n;
        while (cur > 1) {
            byes += cur % 2;
            cur = (cur + 1) / 2;
        }
        return byes;
    }

    /**
     * 逐轮配对下能否产生季军赛。
     *
     * 季军赛需要两场半决赛都有负方；若半决赛轮恰好是「轮空轮」（该轮只有 1 场真实比赛），
     * 另一侧的负方不存在，无法组织季军赛。偶数人参赛时这种情况必然出现。
     */
    public static boolean dynamicThirdPlacePossible(int n) {
        int rounds = roundCountOf(n);
        if (rounds < 2) return false;
        int cur = n;
        for (int r = 1; r < rounds - 1; r++) {
            cur = (cur + 1) / 2;
        }
        return cur / 2 >= 2;
    }

    /**
     * 以「逐轮配对」方式构建淘汰赛对阵树。
     *
     * 与 {@link #buildKnockout} 的区别：不预置 2^n 签位，而是每一轮把当前人数两两配对；
     * 人数为奇数时恰好 1 人轮空并直接进入下一轮。因此
     * <ul>
     *   <li>偶数人参赛 → 首轮没有任何轮空（46 人就是 23 场，而不是 64 签位下的 18 个轮空）</li>
     *   <li>奇数人参赛 → 首轮 1 人轮空（名额给落位最靠前的选手，即最高种子）</li>
     * </ul>
     * 轮空名额优先给「本轮尚未轮空过的已确定选手」，避免同一个人连续轮空。
     *
     * @param order 首轮配对顺序（相邻两人为一对），由抽签引擎给出，保证种子与同单位已尽量分散
     */
    public static List<Match> buildDynamic(String tournamentId, String idPrefix, List<String> order,
                                           String stage, boolean withThirdPlace) {
        List<Match> all = new ArrayList<>();
        if (order == null || order.size() < 2) return all;

        String prefix = safePrefix(idPrefix);
        int rounds = roundCountOf(order.size());

        List<Slot> current = new ArrayList<>();
        for (String id : order) current.add(Slot.ofPlayer(id));

        java.util.Set<String> hadBye = new java.util.HashSet<>();
        int round = 1;
        while (current.size() > 1) {
            List<Slot> list = new ArrayList<>(current);
            List<Slot> next = new ArrayList<>();
            int seq = 1;

            // ---------- 奇数人：1 人轮空 ----------
            if (list.size() % 2 == 1) {
                int idx = pickByeIndex(list, hadBye);
                Slot s = list.remove(idx);
                Match bye = newMatch(tournamentId, prefix, stage, round, rounds, seq++, 0);
                bye.setRound_name(roundNameDynamic(round, rounds));
                bye.setPlayer_b_id(DrawEngine.BYE);
                bye.setRemark("轮空自动晋级");
                if (s.playerId != null) {
                    bye.setPlayer_a_id(s.playerId);
                    hadBye.add(s.playerId);
                } else {
                    // 轮空名额落在「上一轮的胜者」上：先留空，由 advance 填充后自动晋级
                    s.match.setNext_match_id(bye.getMatch_id());
                    s.match.setNext_slot("A");
                }
                all.add(bye);
                next.add(Slot.ofMatch(bye));
            }

            // ---------- 其余两两配对 ----------
            for (int i = 0; i + 1 < list.size(); i += 2) {
                Match m = newMatch(tournamentId, prefix, stage, round, rounds, seq++, 0);
                m.setRound_name(roundNameDynamic(round, rounds));
                link(m, "A", list.get(i));
                link(m, "B", list.get(i + 1));
                all.add(m);
                next.add(Slot.ofMatch(m));
            }

            current = next;
            round++;
        }

        // ---------- 季军赛 ----------
        // 只有「两场半决赛都是真实比赛」时才可能产生季军：若某一侧恰好是轮空轮，
        // 该侧没有负方，无法组织季军赛（逐轮配对下偶数人数必然出现这种情况）。
        if (withThirdPlace && rounds >= 2) {
            List<Match> semis = new ArrayList<>();
            for (Match m : all) {
                if (STAGE_KNOCKOUT.equals(m.getStage()) && m.getRound() != null
                        && m.getRound() == rounds - 1
                        && !DrawEngine.BYE.equals(m.getPlayer_b_id())) {
                    semis.add(m);
                }
            }
            if (semis.size() >= 2) {
                Match third = newMatch(tournamentId, prefix, STAGE_THIRD, rounds, rounds, 1, 0);
                third.setMatch_id(prefix + "_THIRD");
                third.setRound_name("季军赛");
                all.add(third);
                semis.get(0).setLoser_next_id(third.getMatch_id());
                semis.get(0).setLoser_slot("A");
                semis.get(1).setLoser_next_id(third.getMatch_id());
                semis.get(1).setLoser_slot("B");
            }
        }

        resolveByes(all);
        refreshStatus(all);
        return all;
    }

    /**
     * 选择轮空槽位：优先给本轮「尚未轮空过的已确定选手」（首轮即最高优先位），
     * 若本轮全部是待决场次，则给最后一个槽位。
     */
    private static int pickByeIndex(List<Slot> list, java.util.Set<String> hadBye) {
        for (int i = 0; i < list.size(); i++) {
            Slot s = list.get(i);
            if (s.playerId != null && !hadBye.contains(s.playerId)) return i;
        }
        return list.size() - 1;
    }

    /** 把槽位接到目标场次的指定位置：已确定选手直接写入，待决场次则建立晋级指针 */
    private static void link(Match target, String slot, Slot source) {
        if (source == null) return;
        if (source.playerId != null) {
            if ("A".equals(slot)) target.setPlayer_a_id(source.playerId);
            else target.setPlayer_b_id(source.playerId);
            return;
        }
        if (source.match != null) {
            source.match.setNext_match_id(target.getMatch_id());
            source.match.setNext_slot(slot);
        }
    }

    private static Match newMatch(String tournamentId, String prefix, String stage,
                                  int round, int rounds, int order, int groupNo) {
        Match m = new Match();
        m.setMatch_id(prefix + "_R" + round + "_" + order);
        m.setTournament_id(tournamentId);
        m.setStage(stage);
        m.setRound(round);
        m.setRound_name(roundName(round, rounds));
        m.setGroup_no(groupNo);
        m.setMatch_order(order);
        m.setScore_a(0);
        m.setScore_b(0);
        m.setGames_a(0);
        m.setGames_b(0);
        m.setStatus(STATUS_PENDING);
        return m;
    }

    /** 场次ID带前缀，避免小组赛与淘汰赛轮次号冲突 */
    private static String safePrefix(String idPrefix) {
        String p = idPrefix == null ? "m" : idPrefix.replaceAll("[^A-Za-z0-9_]", "");
        return p.length() > 24 ? p.substring(0, 24) : p;
    }

    /**
     * 轮空自动晋级：首轮（及后续出现的）轮空场次，直接把对手推进下一轮
     */
    public static void resolveByes(List<Match> matches) {
        for (int round = 1; round <= 16; round++) {
            List<Match> list = new ArrayList<>();
            for (Match m : matches) {
                if (m.getRound() != null && m.getRound() == round) list.add(m);
            }
            if (list.isEmpty()) break;
            for (Match m : list) {
                if (STATUS_DONE.equals(m.getStatus())) continue;
                boolean aBye = DrawEngine.BYE.equals(m.getPlayer_a_id());
                boolean bBye = DrawEngine.BYE.equals(m.getPlayer_b_id());
                if (aBye && bBye) {
                    m.setStatus(STATUS_BYE);
                    continue;
                }
                if (aBye || bBye) {
                    String winner = aBye ? m.getPlayer_b_id() : m.getPlayer_a_id();
                    if (winner == null) {
                        m.setStatus(STATUS_PENDING);
                        continue;
                    }
                    m.setWinner_id(winner);
                    m.setStatus(STATUS_DONE);
                    m.setRemark("轮空自动晋级");
                    advance(m, matches, winner, false);
                }
            }
        }
    }

    /** 刷新各场次状态：双方就位 → READY，否则 PENDING */
    public static void refreshStatus(List<Match> matches) {
        for (Match m : matches) {
            if (STATUS_DONE.equals(m.getStatus()) || STATUS_BYE.equals(m.getStatus())) continue;
            m.setStatus(isReady(m) ? STATUS_READY : STATUS_PENDING);
        }
    }

    private static boolean isReady(Match m) {
        return m.getPlayer_a_id() != null && m.getPlayer_b_id() != null
                && !DrawEngine.BYE.equals(m.getPlayer_a_id())
                && !DrawEngine.BYE.equals(m.getPlayer_b_id());
    }

    /**
     * 把胜方/负方写入下一场次的对应位置
     *
     * @param loser true 表示写负方（季军赛）
     */
    public static void advance(Match from, List<Match> all, String playerId, boolean loser) {
        String targetId = loser ? from.getLoser_next_id() : from.getNext_match_id();
        String slot = loser ? from.getLoser_slot() : from.getNext_slot();
        if (targetId == null || slot == null) return;
        for (Match next : all) {
            if (targetId.equals(next.getMatch_id())) {
                if ("A".equals(slot)) {
                    if (playerId != null && !playerId.equals(next.getPlayer_a_id())) {
                        next.setPlayer_a_id(playerId);
                    }
                } else if ("B".equals(slot)) {
                    if (playerId != null && !playerId.equals(next.getPlayer_b_id())) {
                        next.setPlayer_b_id(playerId);
                    }
                }
                return;
            }
        }
    }
}
