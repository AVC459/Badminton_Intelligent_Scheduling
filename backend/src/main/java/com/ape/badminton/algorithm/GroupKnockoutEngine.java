package com.ape.badminton.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 小组循环 + 交叉淘汰 混合赛制引擎
 *
 * 流程：
 *   1. 蛇形分组（snake seeding）—— 种子与选手按蛇形轮流分入各组，保证各组实力均衡
 *   2. 组内单循环 —— 复用 {@link RoundRobinEngine}
 *   3. 组间交叉淘汰 —— 各组同序出线者按「奇偶反向」排列后套用标准蛇形落位，
 *      使同组出线选手分处签表两端，最早只能在决赛相遇
 */
public class GroupKnockoutEngine {

    /** 蛇形分组：第 i 个选手应分入的组下标（0 起） */
    public static int snakeIndex(int i, int groupCount) {
        int cycle = i / groupCount;
        int r = i % groupCount;
        return (cycle % 2 == 0) ? r : (groupCount - 1 - r);
    }

    /**
     * 蛇形分组
     *
     * @param players    全部参赛选手
     * @param groupCount 小组数量
     * @param seed       随机种子，固定后可复现
     */
    public static List<List<DrawRequest.PlayerView>> snakeAssign(List<DrawRequest.PlayerView> players,
                                                                 int groupCount, Long seed) {
        List<List<DrawRequest.PlayerView>> groups = new ArrayList<>();
        for (int i = 0; i < groupCount; i++) groups.add(new ArrayList<>());
        if (players.isEmpty() || groupCount <= 1) {
            if (groupCount == 1) groups.get(0).addAll(players);
            return groups;
        }

        Random rnd = seed == null ? new Random() : new Random(seed);
        List<DrawRequest.PlayerView> seeds = players.stream()
                .filter(DrawRequest.PlayerView::isSeed)
                .sorted(Comparator.comparingInt(DrawRequest.PlayerView::getSeedRank))
                .collect(Collectors.toList());
        List<DrawRequest.PlayerView> others = players.stream()
                .filter(p -> !p.isSeed())
                .collect(Collectors.toList());
        Collections.shuffle(others, rnd);

        List<DrawRequest.PlayerView> ordered = new ArrayList<>(seeds);
        ordered.addAll(others);

        for (int i = 0; i < ordered.size(); i++) {
            groups.get(snakeIndex(i, groupCount)).add(ordered.get(i));
        }

        repairTeamWithinGroups(groups);
        return groups;
    }

    /**
     * 组内同单位规避的轻量修正：
     * 每轮至多执行一次有效交换，反复迭代直到无冲突或达到上限。
     */
    private static void repairTeamWithinGroups(List<List<DrawRequest.PlayerView>> groups) {
        int maxPass = 30;
        for (int pass = 0; pass < maxPass; pass++) {
            boolean changed = false;
            for (int gi = 0; gi < groups.size() && !changed; gi++) {
                List<DrawRequest.PlayerView> g = groups.get(gi);
                for (int i = 0; i < g.size() && !changed; i++) {
                    for (int j = i + 1; j < g.size() && !changed; j++) {
                        DrawRequest.PlayerView pj = g.get(j);
                        if (!sameTeam(g.get(i), pj)) continue;
                        if (pj.isSeed()) continue;
                        for (int gj = 0; gj < groups.size() && !changed; gj++) {
                            if (gj == gi) continue;
                            List<DrawRequest.PlayerView> other = groups.get(gj);
                            for (int k = 0; k < other.size() && !changed; k++) {
                                DrawRequest.PlayerView cand = other.get(k);
                                if (cand.isSeed()) continue;
                                // cand 进 gi：不能与 gi 中其余人同单位
                                if (teamCountExcluding(g, team(cand), pj) > 0) continue;
                                // pj 进 gj：不能与 gj 中其余人同单位
                                if (teamCountExcluding(other, team(pj), cand) > 0) continue;
                                other.set(k, pj);
                                g.set(j, cand);
                                changed = true;
                            }
                        }
                    }
                }
            }
            if (!changed) break;
        }
    }

    private static int teamCountExcluding(List<DrawRequest.PlayerView> list, String team, DrawRequest.PlayerView exclude) {
        if (team == null || team.isEmpty()) return 0;
        int c = 0;
        for (DrawRequest.PlayerView x : list) {
            if (x == exclude) continue;
            if (team.equals(team(x))) c++;
        }
        return c;
    }

    private static boolean sameTeam(DrawRequest.PlayerView a, DrawRequest.PlayerView b) {
        String ta = team(a);
        return !ta.isEmpty() && ta.equals(team(b));
    }

    private static String team(DrawRequest.PlayerView p) {
        String t = p == null ? null : p.getTeam();
        return t == null ? "" : t.trim();
    }

    /**
     * 由各组出线名单生成交叉淘汰签位表。
     *
     * <p><b>构造原理（可证明，不再依赖贪心）：</b></p>
     *
     * <p>标准淘汰签表有一个关键性质：按 {@link DrawEngine#seedingOrder(int)} 落位的 rank 中，
     * 相邻的 rank 对 (1,2)、(3,4)、(5,6)…… 恰好被分在签表的上下两个半区，
     * 因此它们<b>最早只能在决赛相遇</b>。</p>
     *
     * <p>于是只要按「组块」顺序排列选手 —— 第 0 组依次取其第 1..k 名，第 1 组再取第 1..k 名……
     * 同组的前两名就必然落在相邻 rank 对上，从而天然分处上下半区；</p>
     *
     * <p>对于 4 组 × 每组出线 2 人的典型场景，落位结果为：</p>
     * <pre>
     *   签位:  0    1    2    3   |   4    5    6    7
     *   选手: A1   D2   C1   B2  |  B1   C2   D1   A2
     *   首轮:  A1-D2   C1-B2    |  B1-C2   D1-A2     ← 全部为跨组对阵
     * </pre>
     * <p>既无首轮同组相残，同组两人也只有在决赛才可能碰面。</p>
     *
     * @param qualifiedByGroup 各组已按名次排好的出线名单（下标 0 为小组第一）
     * @param advance          每组出线人数
     * @param knockoutSize     淘汰赛签位规模（2 的幂）
     */
    public static String[] crossKnockoutPositions(List<List<String>> qualifiedByGroup,
                                                  int advance, int knockoutSize) {
        String[] pos = new String[knockoutSize];
        Arrays.fill(pos, DrawEngine.BYE);
        if (qualifiedByGroup == null || qualifiedByGroup.isEmpty()) return pos;

        // 1) 组块序：按「组」为块依次输出各组第 1..advance 名
        //    使同组前两名占据相邻 rank，从而分处上下半区
        List<String> ordered = new ArrayList<>();
        for (List<String> grp : qualifiedByGroup) {
            for (int j = 0; j < advance && j < grp.size(); j++) {
                ordered.add(grp.get(j));
            }
        }

        // 2) 套用标准蛇形落位；超出选手数的尾部 rank 保持轮空（由强手获得轮空）
        int[] rankToSlot = new int[knockoutSize + 1];
        int[] ord = DrawEngine.seedingOrder(knockoutSize);
        for (int p = 0; p < knockoutSize; p++) {
            rankToSlot[ord[p]] = p;
        }
        int limit = Math.min(ordered.size(), knockoutSize);
        for (int i = 0; i < limit; i++) {
            pos[rankToSlot[i + 1]] = ordered.get(i);
        }
        return pos;
    }

    /**
     * 淘汰赛首轮同单位对阵修正。
     *
     * <p>与通用的 {@code DrawEngine.repairPlacement} 不同，本方法有两条硬性约束：</p>
     * <ul>
     *   <li>换位后<b>不得引入新的同组首轮对阵</b>（保住交叉淘汰的分散效果）；</li>
     *   <li>换位后<b>不得引入新的同单位首轮对阵</b>。</li>
     * </ul>
     * <p>因此它只会让首轮同单位冲突单调减少，绝不会像通用修复那样把已经排好的分组结构打乱。</p>
     *
     * @return 实际执行的换位次数
     */
    public static int repairKnockoutClashes(String[] positions,
                                            Map<String, String> teamOf,
                                            Map<String, String> groupOf,
                                            int knockoutSize) {
        if (positions == null || knockoutSize < 4) return 0;
        int pairCount = knockoutSize / 2;
        int repairs = 0;
        boolean changed = true;
        int guard = 0;

        while (changed && guard++ < 60) {
            changed = false;
            for (int p = 0; p < pairCount && !changed; p++) {
                int a = p * 2;
                int b = p * 2 + 1;
                if (!sameTeam(positions[a], positions[b], teamOf)) continue;

                for (int q = 0; q < pairCount && !changed; q++) {
                    if (q == p) continue;
                    int c = q * 2;
                    int d = q * 2 + 1;
                    int[][] candidates = {{a, c}, {a, d}, {b, c}, {b, d}};
                    for (int[] sw : candidates) {
                        if (!swapIsSafe(positions, sw[0], sw[1], teamOf, groupOf, knockoutSize)) continue;
                        String tmp = positions[sw[0]];
                        positions[sw[0]] = positions[sw[1]];
                        positions[sw[1]] = tmp;
                        changed = true;
                        repairs++;
                        break;
                    }
                }
            }
        }
        return repairs;
    }

    /** 换位后，涉及的两场首轮对阵都不含同单位、也不含同组选手 */
    private static boolean swapIsSafe(String[] pos, int x, int y,
                                      Map<String, String> teamOf, Map<String, String> groupOf,
                                      int size) {
        if (x == y) return false;
        String[] cp = pos.clone();
        String tmp = cp[x];
        cp[x] = cp[y];
        cp[y] = tmp;
        return pairIsClean(cp, x, teamOf, groupOf, size)
                && pairIsClean(cp, y, teamOf, groupOf, size);
    }

    private static boolean pairIsClean(String[] pos, int slot,
                                       Map<String, String> teamOf, Map<String, String> groupOf,
                                       int size) {
        int partner = (slot % 2 == 0) ? slot + 1 : slot - 1;
        if (partner < 0 || partner >= size) return true;
        String s1 = pos[slot];
        String s2 = pos[partner];
        if (DrawEngine.BYE.equals(s1) || DrawEngine.BYE.equals(s2)) return true;
        if (s1 == null || s2 == null) return true;
        if (sameTeam(s1, s2, teamOf)) return false;
        String g1 = groupOf == null ? null : groupOf.get(s1);
        String g2 = groupOf == null ? null : groupOf.get(s2);
        return !(g1 != null && g1.equals(g2));
    }

    /**
     * 逐轮配对交叉淘汰（动态签表）的首轮冲突修正。
     *
     * <p>动态签表没有 2^n 签位，只有一条「首轮配对顺序」：相邻两人即首轮对手。
     * 这里在顺序表上做贪心换位，且换位后新旧两个配对都必须干净才执行，
     * 因此同组 / 同单位的首轮冲突只会单调减少，不会按下葫芦起了瓢。</p>
     *
     * <p>约束两条：</p>
     * <ul>
     *   <li>同组两人首轮不得相遇 —— 这是小组赛的价值所在，必须始终保证；</li>
     *   <li>同单位两人首轮不得相遇 —— 仅在赛事开启了「同单位规避」时生效。</li>
     * </ul>
     *
     * @return 实际执行的换位次数
     */
    public static int repairOrderClashes(List<String> order,
                                         Map<String, String> teamOf,
                                         Map<String, String> groupOf,
                                         boolean avoidSameTeam) {
        if (order == null || order.size() < 4) return 0;
        int pairCount = order.size() / 2;
        int repairs = 0;
        boolean changed = true;
        int guard = 0;

        while (changed && guard++ < 60) {
            changed = false;
            for (int p = 0; p < pairCount && !changed; p++) {
                if (orderPairIsClean(order, p * 2, teamOf, groupOf, avoidSameTeam)) continue;

                for (int q = 0; q < pairCount && !changed; q++) {
                    if (q == p) continue;
                    int a = p * 2, b = p * 2 + 1, c = q * 2, d = q * 2 + 1;
                    int[][] candidates = {{a, c}, {a, d}, {b, c}, {b, d}};
                    for (int[] sw : candidates) {
                        swap(order, sw[0], sw[1]);
                        if (orderPairIsClean(order, a, teamOf, groupOf, avoidSameTeam)
                                && orderPairIsClean(order, c, teamOf, groupOf, avoidSameTeam)) {
                            changed = true;
                            repairs++;
                            break;
                        }
                        swap(order, sw[0], sw[1]); // 不干净则回退
                    }
                }
            }
        }
        return repairs;
    }

    /** 顺序表上第 slot 个配对是否干净（不同组、且必要时不同单位） */
    private static boolean orderPairIsClean(List<String> order, int slot,
                                            Map<String, String> teamOf,
                                            Map<String, String> groupOf,
                                            boolean avoidSameTeam) {
        int partner = (slot % 2 == 0) ? slot + 1 : slot - 1;
        if (partner < 0 || partner >= order.size()) return true;
        String s1 = order.get(slot);
        String s2 = order.get(partner);
        if (s1 == null || s2 == null) return true;
        if (avoidSameTeam && sameTeam(s1, s2, teamOf)) return false;
        String g1 = groupOf == null ? null : groupOf.get(s1);
        String g2 = groupOf == null ? null : groupOf.get(s2);
        return !(g1 != null && g1.equals(g2));
    }

    private static void swap(List<String> list, int i, int j) {
        String tmp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, tmp);
    }

    private static boolean sameTeam(String a, String b, Map<String, String> teamOf) {
        if (a == null || b == null || teamOf == null) return false;
        String ta = teamOf.get(a);
        return ta != null && !ta.isEmpty() && ta.equals(teamOf.get(b));
    }

    /** 按标准蛇形落位顺序把有序名单放入签位表，空余位置为轮空 */
    public static String[] placeBySeedingOrder(List<String> ordered, int knockoutSize) {
        String[] pos = new String[knockoutSize];
        Arrays.fill(pos, DrawEngine.BYE);
        int[] ord = DrawEngine.seedingOrder(knockoutSize);
        Map<Integer, Integer> rankToSlot = new HashMap<>();
        for (int p = 0; p < knockoutSize; p++) {
            rankToSlot.put(ord[p], p);
        }
        for (int i = 0; i < ordered.size() && i < knockoutSize; i++) {
            Integer slot = rankToSlot.get(i + 1);
            if (slot != null) {
                pos[slot] = ordered.get(i);
            }
        }
        return pos;
    }

    /** 淘汰赛阶段所需轮数 */
    public static int knockoutRounds(int qualifierCount) {
        return DrawEngine.roundsOf(DrawEngine.bracketSize(qualifierCount));
    }

    /** 由各组积分榜提取有序出线名单 */
    public static List<List<String>> toQualifiedLists(List<List<Standing>> groupStandings, int advance) {
        List<List<String>> out = new ArrayList<>();
        for (List<Standing> gs : groupStandings) {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < Math.min(advance, gs.size()); i++) {
                ids.add(gs.get(i).getPlayerId());
            }
            out.add(ids);
        }
        return out;
    }

    /** 小组名称：A、B、C… */
    public static String groupName(int groupNo) {
        if (groupNo >= 1 && groupNo <= 26) {
            return String.valueOf((char) ('A' + groupNo - 1));
        }
        return "第" + groupNo + "组";
    }

    /** 小组数建议值：优先采用调用方偏好，否则保证每组至少 3 人 */
    public static int suggestGroupCount(int playerCount, int preferred) {
        if (playerCount <= 3) return 1;
        if (preferred >= 2 && playerCount / preferred >= 3) return preferred;
        for (int g = 4; g >= 2; g--) {
            if (playerCount / g >= 3) return g;
        }
        return 2;
    }

    // ========================================================================
    //  分组公平性度量
    // ========================================================================

    /**
     * 分组公平性分析
     *
     * <p>对应方案书「数据可视化看板：实时展示分组概率分布、种子覆盖度等指标」。
     * 从四个维度度量蛇形分组结果的公平程度：</p>
     *
     * <ol>
     *   <li><b>规模均衡</b>：各组人数差异（受总人数能否整除影响）；</li>
     *   <li><b>种子覆盖</b>：每组是否都有种子选手，避免「死亡之组」；</li>
     *   <li><b>实力均衡</b>：各组实力总分（level 之和）的标准差与极差；</li>
     *   <li><b>同单位规避</b>：同单位选手落入同组视为死签，越少越好。</li>
     * </ol>
     *
     * 综合得分 0~100：实力均衡 60 分 + 种子覆盖 20 分 + 同单位规避 20 分。
     */
    public static FairnessReport analyzeGroups(List<List<DrawRequest.PlayerView>> groups) {
        FairnessReport r = new FairnessReport();
        r.setFormat("GROUP_KNOCKOUT");
        if (groups == null || groups.isEmpty()) {
            r.addNote("尚未分组");
            return r;
        }

        List<Integer> sizes = new ArrayList<>();
        List<Integer> seedCounts = new ArrayList<>();
        List<Integer> levelSums = new ArrayList<>();
        int total = 0;
        int sameTeamPairs = 0;
        int groupsWithoutSeed = 0;

        for (List<DrawRequest.PlayerView> g : groups) {
            sizes.add(g.size());
            int seeds = 0;
            int levelSum = 0;
            for (DrawRequest.PlayerView p : g) {
                if (p.isSeed()) seeds++;
                levelSum += p.getLevel();
            }
            seedCounts.add(seeds);
            levelSums.add(levelSum);
            total += g.size();
            if (seeds == 0) groupsWithoutSeed++;
            sameTeamPairs += countSameTeamPairs(g);
        }

        r.setGroupSizes(sizes);
        r.setSeedPerGroup(seedCounts);
        r.setLevelPerGroup(levelSums);
        r.setSameTeamInGroup(sameTeamPairs);
        r.setGroupsWithoutSeed(groupsWithoutSeed);

        int maxSize = sizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        int minSize = sizes.stream().mapToInt(Integer::intValue).min().orElse(0);
        r.setGroupSizeSpread(maxSize - minSize);

        double mean = levelSums.isEmpty() ? 0
                : levelSums.stream().mapToInt(Integer::intValue).sum() * 1.0 / levelSums.size();
        double var = 0;
        for (int v : levelSums) var += (v - mean) * (v - mean);
        var = levelSums.isEmpty() ? 0 : var / levelSums.size();
        r.setGroupLevelStdDev(Math.sqrt(var));
        int maxLevel = levelSums.stream().mapToInt(Integer::intValue).max().orElse(0);
        int minLevel = levelSums.stream().mapToInt(Integer::intValue).min().orElse(0);
        r.setGroupLevelRange(maxLevel - minLevel);

        // ---------------- 综合评分 ----------------
        double balanceScore;
        if (mean <= 0) {
            balanceScore = 60;
        } else {
            // 标准差占均值的比例越低，得分越高；相对离散度 0 → 60 分，≥50% → 0 分
            double rel = r.getGroupLevelStdDev() / mean;
            balanceScore = Math.max(0, Math.min(60, 60 * (1 - rel / 0.5)));
        }
        double seedScore = groups.isEmpty() ? 20
                : 20.0 * (groups.size() - groupsWithoutSeed) / groups.size();
        double avoidScore = total == 0 ? 20
                : Math.max(0, 20 - sameTeamPairs * 5.0);

        r.setScore(Math.round((balanceScore + seedScore + avoidScore) * 10.0) / 10.0);
        r.setAvoidanceRate(sameTeamPairs == 0 ? 1.0 : Math.max(0, 1 - sameTeamPairs * 1.0 / Math.max(1, total / 2)));

        // ---------------- 风险提示 ----------------
        if (groupsWithoutSeed > 0) {
            r.addNote("有 " + groupsWithoutSeed + " 个小组没有种子选手，存在「死亡之组」风险");
        }
        if (sameTeamPairs > 0) {
            r.addNote("存在 " + sameTeamPairs + " 对同单位选手被分入同组，无法完全规避");
        } else {
            r.addNote("同单位选手全部规避成功，无同组死签");
        }
        if (r.getGroupLevelRange() > 0) {
            r.addNote("各组实力总分极差为 " + r.getGroupLevelRange() + "，"
                    + (r.getGroupLevelRange() <= 2 ? "分组较为均衡" : "建议调整种子或重新分组"));
        }
        if (r.getGroupSizeSpread() > 0) {
            r.addNote("因参赛人数不能整除，各组人数相差 " + r.getGroupSizeSpread()
                    + " 人，轮次会略有差异");
        }
        return r;
    }

    private static int countSameTeamPairs(List<DrawRequest.PlayerView> g) {
        int c = 0;
        for (int i = 0; i < g.size(); i++) {
            for (int j = i + 1; j < g.size(); j++) {
                if (sameTeam(g.get(i), g.get(j))) c++;
            }
        }
        return c;
    }
}
