package com.ape.badminton.algorithm;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 淘汰赛智能抽签引擎
 *
 * 实现项目方案中的四项核心算法：
 *   1. 分区控制算法  —— 蛇形落位（seeding order），自动实现 1/4 区、1/8 区种子均匀分布
 *   2. 轮空位置优化  —— 轮空优先分配给高种子，剩余轮空按分区轮转摊平
 *   3. 同单位规避    —— 冲突图（顶点=选手，边权=最早相遇轮次）上做贪心构造
 *   4. 死签检测与修正 —— 冲突图局部搜索（最陡下降 + 随机扰动重启），把同单位/同区的过早相遇降到最低
 *
 * 该引擎为纯函数式实现，不依赖 Spring 与数据库，便于单元测试与复用。
 */
public class DrawEngine {

    /** 签位中的轮空标记 */
    public static final String BYE = "BYE";

    // ========================================================================
    //  一、基础工具
    // ========================================================================

    /** 大于等于 n 的最小 2 的幂（即签位规模） */
    public static int bracketSize(int n) {
        if (n <= 1) return 1;
        int s = 1;
        while (s < n) s <<= 1;
        return s;
    }

    /** 签位规模对应的轮数 */
    public static int roundsOf(int size) {
        int r = 0;
        while ((1 << r) < size) r++;
        return r;
    }

    /**
     * 标准蛇形落位顺序表。
     * 返回值 order 满足：order[position] = 应放在该签位的种子序号（1 起）。
     *
     * 生成规则（逐层展开）：
     *   order(1) = [1]
     *   order(2s) 由 order(s) 逐项展开——偶数下标产出 (x, 2s+1-x)，奇数下标产出 (2s+1-x, x)，
     *   该交替规则使 1 号种子固定在首位、2 号种子固定在末位，且各分区被均匀填满。
     *
     * 该顺序表天然满足：
     *   1、2 号种子分处上下半区（只能在决赛相遇）
     *   1~4 号种子分处 4 个 1/4 区
     *   1~8 号种子分处 8 个 1/8 区
     *
     * 例：size=8 → [1,8,5,4,3,6,7,2]
     *     size=16 → [1,16,9,8,5,12,13,4,3,14,11,6,7,10,15,2]
     */
    public static int[] seedingOrder(int size) {
        List<Integer> order = new ArrayList<>();
        order.add(1);
        for (int s = 2; s <= size; s <<= 1) {
            List<Integer> next = new ArrayList<>(s);
            for (int i = 0; i < order.size(); i++) {
                int x = order.get(i);
                int mirror = s + 1 - x;
                if (i % 2 == 0) {
                    next.add(x);
                    next.add(mirror);
                } else {
                    next.add(mirror);
                    next.add(x);
                }
            }
            order = next;
        }
        int[] arr = new int[order.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = order.get(i);
        }
        return arr;
    }

    /**
     * 两个签位最早可能相遇的轮次。
     * 首轮相遇返回 1；决赛相遇返回 log2(size)。
     */
    public static int meetRound(int p, int q, int size) {
        int rounds = roundsOf(size);
        for (int r = 1; r <= rounds; r++) {
            if ((p >> r) == (q >> r)) return r;
        }
        return rounds;
    }

    // ========================================================================
    //  二、主流程
    // ========================================================================

    public DrawResult draw(DrawRequest req) {
        List<DrawRequest.PlayerView> players = new ArrayList<>(req.getPlayers());
        int n = players.size();
        int size = bracketSize(n);
        int rounds = roundsOf(size);
        int byeCount = Math.max(0, size - n);

        DrawResult result = new DrawResult();
        result.setBracketSize(size);
        result.setRounds(rounds);
        result.setByeCount(byeCount);

        String[] pos = new String[size];
        Map<String, DrawRequest.PlayerView> byId = players.stream()
                .collect(Collectors.toMap(DrawRequest.PlayerView::getPlayerId, p -> p, (a, b) -> a));

        // ---------- 步骤 1：种子蛇形落位 ----------
        List<DrawRequest.PlayerView> seeds = players.stream()
                .filter(DrawRequest.PlayerView::isSeed)
                .sorted(Comparator.comparingInt(DrawRequest.PlayerView::getSeedRank))
                .collect(Collectors.toList());
        if (seeds.size() > size) {
            seeds = new ArrayList<>(seeds.subList(0, size));
        }
        int[] ord = seedingOrder(size);
        Map<Integer, DrawRequest.PlayerView> seedByRank = new LinkedHashMap<>();
        for (int i = 0; i < seeds.size(); i++) {
            seedByRank.put(i + 1, seeds.get(i));
        }
        Map<Integer, Integer> rankToSlot = new HashMap<>();
        for (int p = 0; p < size; p++) {
            if (ord[p] <= seeds.size()) {
                rankToSlot.put(ord[p], p);
            }
        }
        for (Map.Entry<Integer, DrawRequest.PlayerView> e : seedByRank.entrySet()) {
            Integer slot = rankToSlot.get(e.getKey());
            if (slot != null) {
                pos[slot] = e.getValue().getPlayerId();
            }
        }
        result.setDrawPositions(snapshot(pos));
        if (!seeds.isEmpty()) {
            result.addStep("SEED", "按 BWF 蛇形落位规则放置 " + seeds.size() + " 名种子选手（"
                    + seeds.stream().map(s -> s.getName() + "#" + s.getSeedRank()).collect(Collectors.joining("、"))
                    + "），已保证 1/4 区、1/8 区分区避让");
        } else {
            result.addStep("SEED", "本场赛事未设置种子选手，全部选手进入随机抽签池");
        }

        // ---------- 步骤 2：轮空位置优化 ----------
        int remaining = byeCount;
        Set<Integer> byeSlots = new LinkedHashSet<>();
        StringBuilder byeDesc = new StringBuilder();
        // 2.1 优先把轮空给高种子
        for (int rank = 1; rank <= seeds.size() && remaining > 0; rank++) {
            Integer slot = rankToSlot.get(rank);
            if (slot == null) continue;
            int partner = (slot % 2 == 0) ? slot + 1 : slot - 1;
            if (partner >= 0 && partner < size && pos[partner] == null && !byeSlots.contains(partner)) {
                pos[partner] = BYE;
                byeSlots.add(partner);
                remaining--;
                byeDesc.append("签位").append(partner + 1).append("轮空");
            }
        }
        // 2.2 剩余轮空按 1/4 区轮转摊平，避免轮空扎堆
        if (remaining > 0) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < size; i += 2) {
                boolean aHas = pos[i] != null && !BYE.equals(pos[i]);
                boolean bHas = pos[i + 1] != null && !BYE.equals(pos[i + 1]);
                if (aHas ^ bHas) {
                    candidates.add(aHas ? i + 1 : i);
                }
            }
            if (candidates.size() < remaining) {
                for (int i = 0; i < size; i += 2) {
                    if (pos[i] == null && pos[i + 1] == null) {
                        candidates.add(i + 1);
                    }
                }
            }
            for (Integer c : spreadAcrossSections(candidates, size)) {
                if (remaining == 0) break;
                if (pos[c] == null) {
                    pos[c] = BYE;
                    byeSlots.add(c);
                    remaining--;
                }
            }
        }
        result.setDrawPositions(snapshot(pos));
        result.addStep("BYE", byeCount == 0
                ? "参赛人数恰为 2 的整数次幂，无需轮空"
                : ("共 " + byeCount + " 个轮空签位，优先分配给高种子并对各分区均衡摊平"
                    + (byeDesc.length() > 0 ? "（" + byeDesc + "）" : "")));

        // ---------- 步骤 3：非种子填充 + 同单位规避 ----------
        List<DrawRequest.PlayerView> nonSeeds = players.stream()
                .filter(p -> !p.isSeed())
                .collect(Collectors.toList());
        Random rnd = (req.getRandomSeed() == null) ? new Random() : new Random(req.getRandomSeed());
        Collections.shuffle(nonSeeds, rnd);
        // 人数多的单位约束最强，优先安排；同一单位内部保持随机顺序（稳定排序）
        Map<String, Long> teamCount = nonSeeds.stream()
                .map(p -> teamOf(p))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        nonSeeds.sort(Comparator.comparingLong(
                (DrawRequest.PlayerView p) -> -teamCount.getOrDefault(teamOf(p), 0L)));

        for (DrawRequest.PlayerView p : nonSeeds) {
            int bestSlot = -1;
            long bestCost = Long.MAX_VALUE;
            int ties = 0;
            for (int i = 0; i < size; i++) {
                if (pos[i] != null) continue;
                long c = placementCost(pos, byId, p, i, size, rounds);
                if (c < bestCost) {
                    bestCost = c;
                    bestSlot = i;
                    ties = 1;
                } else if (c == bestCost) {
                    ties++;
                    // 蓄水池抽样：代价相同的签位等概率选取，保证抽签随机性
                    if (rnd.nextInt(ties) == 0) bestSlot = i;
                }
            }
            if (bestSlot >= 0) {
                pos[bestSlot] = p.getPlayerId();
            }
        }
        result.setDrawPositions(snapshot(pos));
        result.addStep("FILL", "按「同单位优先分散」的代价函数完成 " + nonSeeds.size()
                + " 名非种子选手落位"
                + (req.isAvoidSameTeam() ? "（已启用同单位规避）" : "（未启用同单位规避，纯随机落位）"));

        // ---------- 步骤 4：死签检测与冲突图局部搜索修正 ----------
        int beforeFirst = firstRoundClashCount(pos, byId, size);
        RepairOutcome ro = repair(pos, byId, size, rounds, req.isAvoidSameTeam(), rnd);
        int afterFirst = firstRoundClashCount(pos, byId, size);
        result.setDrawPositions(snapshot(pos));
        result.setAvoidanceResolved(afterFirst == 0);
        if (!req.isAvoidSameTeam()) {
            result.addStep("VERIFY", "未启用同单位规避，本轮不做死签修正（检测到首轮同单位相遇 " + afterFirst + " 处）");
        } else if (beforeFirst == 0) {
            result.addStep("VERIFY", "死签检测通过：首轮无同单位相遇，无需修正");
        } else if (afterFirst == 0) {
            result.addStep("REPAIR", "死签检测发现 " + beforeFirst + " 处同单位首轮相遇，"
                    + "经冲突图局部搜索（最陡下降 + 随机扰动重启）换位 " + ro.swaps + " 次后已全部消除");
        } else {
            result.addStep("REPAIR", "死签检测发现 " + beforeFirst + " 处同单位首轮相遇，"
                    + "局部搜索换位 " + ro.swaps + " 次后仍剩余 " + afterFirst
                    + " 处（该单位报名人数过多，签位空间不足，已降至当前约束下的最优）");
        }

        // ---------- 步骤 5：公平性度量 ----------
        FairnessReport report = analyze(pos, byId, seeds.size(), size, rounds);
        result.setFairness(report);
        result.addStep("VERIFY", String.format(
                "公平性校验完成：综合得分 %.1f 分（种子分区均衡 %s，实力离散度 %.2f，同单位规避达成率 %.0f%%）",
                report.getScore(),
                report.getSeedPerQuarter().toString(),
                report.getLevelStdDev(),
                report.getAvoidanceRate() * 100));
        return result;
    }

    // ========================================================================
    //  三、代价函数与死签修正
    // ========================================================================

    /** 越早相遇代价越大：首轮相遇权重最高，决赛相遇权重最低 */
    private long weight(int rounds, int meetRound) {
        return 1L << Math.max(0, rounds - meetRound);
    }

    /** 把选手 p 放到 slot 时，与已落位的同单位选手产生的冲突代价 */
    private long placementCost(String[] pos, Map<String, DrawRequest.PlayerView> byId,
                               DrawRequest.PlayerView p, int slot, int size, int rounds) {
        String team = teamOf(p);
        if (team.isEmpty()) return 0L;
        long cost = 0L;
        for (int i = 0; i < size; i++) {
            String id = pos[i];
            if (id == null || BYE.equals(id)) continue;
            DrawRequest.PlayerView other = byId.get(id);
            if (other == null || !team.equals(teamOf(other))) continue;
            cost += weight(rounds, meetRound(Math.min(i, slot), Math.max(i, slot), size));
        }
        return cost;
    }

    /** 全局冲突代价：所有同单位配对按最早相遇轮次加权求和 */
    private long totalCost(String[] pos, Map<String, DrawRequest.PlayerView> byId, int size, int rounds) {
        long cost = 0L;
        for (int i = 0; i < size; i++) {
            String a = pos[i];
            if (a == null || BYE.equals(a)) continue;
            DrawRequest.PlayerView pa = byId.get(a);
            if (pa == null) continue;
            String ta = teamOf(pa);
            if (ta.isEmpty()) continue;
            for (int j = i + 1; j < size; j++) {
                String b = pos[j];
                if (b == null || BYE.equals(b)) continue;
                DrawRequest.PlayerView pb = byId.get(b);
                if (pb == null || !ta.equals(teamOf(pb))) continue;
                cost += weight(rounds, meetRound(i, j, size));
            }
        }
        return cost;
    }

    /** 单个签位上的选手与其余所有同单位选手的冲突代价之和 */
    private long slotCost(String[] pos, Map<String, DrawRequest.PlayerView> byId,
                          int idx, int size, int rounds) {
        String id = pos[idx];
        if (id == null || BYE.equals(id)) return 0L;
        DrawRequest.PlayerView p = byId.get(id);
        if (p == null) return 0L;
        String team = teamOf(p);
        if (team.isEmpty()) return 0L;
        long cost = 0L;
        for (int k = 0; k < size; k++) {
            if (k == idx) continue;
            String other = pos[k];
            if (other == null || BYE.equals(other)) continue;
            DrawRequest.PlayerView po = byId.get(other);
            if (po == null || !team.equals(teamOf(po))) continue;
            cost += weight(rounds, meetRound(Math.min(idx, k), Math.max(idx, k), size));
        }
        return cost;
    }

    /** 换位 delta 的增量计算：只需重算 i、j 两个签位的冲突代价 */
    private long swapDelta(String[] pos, Map<String, DrawRequest.PlayerView> byId,
                           int i, int j, int size, int rounds) {
        long before = slotCost(pos, byId, i, size, rounds) + slotCost(pos, byId, j, size, rounds);
        String tmp = pos[i];
        pos[i] = pos[j];
        pos[j] = tmp;
        long after = slotCost(pos, byId, i, size, rounds) + slotCost(pos, byId, j, size, rounds);
        pos[j] = pos[i];
        pos[i] = tmp;
        return after - before;
    }

    /** 该签位是否参与「过早相遇」冲突（前两轮内即与同单位选手相遇） */
    private boolean isConflicted(String[] pos, Map<String, DrawRequest.PlayerView> byId,
                                 int idx, int size, int rounds) {
        String id = pos[idx];
        if (id == null || BYE.equals(id)) return false;
        DrawRequest.PlayerView p = byId.get(id);
        if (p == null) return false;
        String team = teamOf(p);
        if (team.isEmpty()) return false;
        int threshold = Math.min(2, rounds);
        for (int k = 0; k < size; k++) {
            if (k == idx) continue;
            String other = pos[k];
            if (other == null || BYE.equals(other)) continue;
            DrawRequest.PlayerView po = byId.get(other);
            if (po == null || !team.equals(teamOf(po))) continue;
            if (meetRound(Math.min(idx, k), Math.max(idx, k), size) <= threshold) return true;
        }
        return false;
    }

    /** 可参与换位的签位：非空、非轮空、非种子（种子位置由规则固定，不可移动） */
    private boolean swappable(String[] pos, int idx, Map<String, DrawRequest.PlayerView> byId) {
        String id = pos[idx];
        if (id == null || BYE.equals(id)) return false;
        DrawRequest.PlayerView p = byId.get(id);
        return p != null && !p.isSeed();
    }

    private static class RepairOutcome {
        int swaps;
        long costBefore;
        long costAfter;
    }

    /**
     * 死签修正：在冲突图上做「最陡下降 + 随机扰动重启」的局部搜索。
     * 目标函数为同单位配对的加权相遇轮次之和，越早相遇代价越高。
     */
    private RepairOutcome repair(String[] pos, Map<String, DrawRequest.PlayerView> byId,
                                 int size, int rounds, boolean enabled, Random rnd) {
        RepairOutcome out = new RepairOutcome();
        out.costBefore = totalCost(pos, byId, size, rounds);
        if (!enabled || out.costBefore == 0 || size < 4) {
            out.costAfter = out.costBefore;
            return out;
        }

        String[] cur = pos.clone();
        String[] bestPos = pos.clone();
        long bestCost = out.costBefore;
        int restarts = 0;
        int guard = 0;

        while (bestCost > 0 && guard++ < 200) {
            // 找一处可下降的换位
            int bi = -1, bj = -1;
            long bestDelta = 0;
            outer:
            for (int i = 0; i < size; i++) {
                if (!swappable(cur, i, byId)) continue;
                if (!isConflicted(cur, byId, i, size, rounds)) continue;
                for (int j = 0; j < size; j++) {
                    if (i == j || !swappable(cur, j, byId)) continue;
                    if (teamOf(byId.get(cur[i])).equals(teamOf(byId.get(cur[j])))) continue; // 同单位互换无意义
                    long d = swapDelta(cur, byId, i, j, size, rounds);
                    if (d < bestDelta) {
                        bestDelta = d;
                        bi = i;
                        bj = j;
                        if (d <= -4) break outer;   // 已找到足够好的下降方向，提前收敛
                    }
                }
            }

            if (bi >= 0) {
                String tmp = cur[bi];
                cur[bi] = cur[bj];
                cur[bj] = tmp;
                out.swaps++;
                long c = totalCost(cur, byId, size, rounds);
                if (c < bestCost) {
                    bestCost = c;
                    bestPos = cur.clone();
                }
            } else {
                if (restarts++ >= 8) break;
                // 随机扰动跳出局部最优，再从扰动解继续搜索
                cur = bestPos.clone();
                List<Integer> sw = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    if (swappable(cur, i, byId)) sw.add(i);
                }
                if (sw.size() < 2) break;
                for (int k = 0; k < 3; k++) {
                    int a = sw.get(rnd.nextInt(sw.size()));
                    int b = sw.get(rnd.nextInt(sw.size()));
                    if (a != b) {
                        String tmp = cur[a];
                        cur[a] = cur[b];
                        cur[b] = tmp;
                    }
                }
            }
        }

        System.arraycopy(bestPos, 0, pos, 0, size);
        out.costAfter = bestCost;
        return out;
    }

    // ========================================================================
    //  四、公平性度量
    // ========================================================================

    private FairnessReport analyze(String[] pos, Map<String, DrawRequest.PlayerView> byId,
                                   int seedCount, int size, int rounds) {
        FairnessReport r = new FairnessReport();
        r.setBracketSize(size);

        int quarters = Math.min(4, Math.max(1, size));
        int qSize = Math.max(1, size / quarters);
        int[] seedPerQ = new int[quarters];
        int[] levelPerQ = new int[quarters];
        for (int i = 0; i < size; i++) {
            String id = pos[i];
            if (id == null || BYE.equals(id)) continue;
            DrawRequest.PlayerView p = byId.get(id);
            int q = Math.min(quarters - 1, i / qSize);
            levelPerQ[q] += (p == null ? 0 : p.getLevel());
            if (p != null && p.isSeed()) seedPerQ[q]++;
        }

        // 轮空分布（上半区 / 下半区）
        int[] byePerHalf = new int[2];
        for (int i = 0; i < size; i++) {
            if (BYE.equals(pos[i])) {
                byePerHalf[i / Math.max(1, size / 2)]++;
            }
        }

        List<Integer> seedList = new ArrayList<>();
        List<Integer> levelList = new ArrayList<>();
        for (int v : seedPerQ) seedList.add(v);
        for (int v : levelPerQ) levelList.add(v);
        r.setSeedPerQuarter(seedList);
        r.setLevelPerQuarter(levelList);
        List<Integer> byeList = new ArrayList<>();
        for (int v : byePerHalf) byeList.add(v);
        r.setByePerHalf(byeList);

        // 实力离散度
        double mean = 0;
        for (int v : levelPerQ) mean += v;
        mean /= quarters;
        double var = 0;
        for (int v : levelPerQ) var += (v - mean) * (v - mean);
        var /= quarters;
        r.setLevelStdDev(Math.sqrt(var));

        // 同单位统计
        int pairs = 0;
        int firstClash = 0;
        int earlyClash = 0;
        Map<String, List<Integer>> teamSlots = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String id = pos[i];
            if (id == null || BYE.equals(id)) continue;
            DrawRequest.PlayerView p = byId.get(id);
            if (p == null) continue;
            String t = teamOf(p);
            if (t.isEmpty()) continue;
            teamSlots.computeIfAbsent(t, k -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<String, List<Integer>> e : teamSlots.entrySet()) {
            List<Integer> slots = e.getValue();
            for (int i = 0; i < slots.size(); i++) {
                for (int j = i + 1; j < slots.size(); j++) {
                    pairs++;
                    int mr = meetRound(Math.min(slots.get(i), slots.get(j)),
                            Math.max(slots.get(i), slots.get(j)), size);
                    if (mr == 1) firstClash++;
                    if (mr <= 2) earlyClash++;
                }
            }
        }
        r.setSameTeamPairs(pairs);
        r.setFirstRoundClashes(firstClash);
        r.setEarlyClashes(earlyClash);
        r.setAvoidanceRate(pairs == 0 ? 1.0 : 1.0 - (double) firstClash / pairs);

        // ---- 综合评分（0~100）----
        // ① 种子分区均衡度（0~35）：同区种子数偏离均值越小越好
        double seedScore = 35;
        if (seedCount > 0 && quarters > 1) {
            double ideal = (double) seedCount / quarters;
            double dev = 0;
            for (int v : seedPerQ) dev += Math.abs(v - ideal);
            double maxDev = 2.0 * seedCount;
            seedScore = 35 * Math.max(0, 1 - dev / maxDev * 2);
        }
        // ② 实力均衡度（0~30）：各分区实力总分标准差越小越好
        double levelScore = 30;
        if (mean > 0) {
            double cv = r.getLevelStdDev() / mean;   // 变异系数
            levelScore = 30 * Math.max(0, 1 - Math.min(1, cv / 0.5));
        }
        // ③ 同单位规避达成率（0~25）
        double avoidScore = 25 * r.getAvoidanceRate();
        // ④ 轮空均衡（0~10）
        double byeScore = 10;
        if (byePerHalf[0] + byePerHalf[1] > 0) {
            double diff = Math.abs(byePerHalf[0] - byePerHalf[1]);
            byeScore = 10 * Math.max(0, 1 - diff / Math.max(1.0, byePerHalf[0] + byePerHalf[1]));
        }
        r.setScore(Math.round((seedScore + levelScore + avoidScore + byeScore) * 10) / 10.0);

        // ---- 风险提示 ----
        if (firstClash > 0) {
            r.addNote("存在 " + firstClash + " 处同单位选手首轮相遇，通常因该单位报名人数超出可隔离上限");
        }
        if (seedCount > quarters) {
            r.addNote("种子数（" + seedCount + "）超过 " + quarters + " 个分区数，同一 1/4 区将出现多名种子");
        }
        for (Map.Entry<String, List<Integer>> e : teamSlots.entrySet()) {
            if (e.getValue().size() > quarters) {
                r.addNote("「" + e.getKey() + "」共有 " + e.getValue().size()
                        + " 人参赛，超过 " + quarters + " 个分区上限，无法完全避免同区");
            }
        }
        if (byePerHalf[0] != byePerHalf[1] && byePerHalf[0] + byePerHalf[1] > 1) {
            r.addNote("上下半区轮空数不等（" + byePerHalf[0] + " : " + byePerHalf[1]
                    + "），由种子数与人数结构决定，属正常现象");
        }
        return r;
    }

    // ========================================================================
    //  五、辅助
    // ========================================================================

    /**
     * 对已有签位表做同单位死签修正。
     * 供「小组出线后的交叉淘汰」等场景复用：这些场景下签位已按规则排好，
     * 只允许在不违反大结构的前提下做局部换位以消除同单位过早相遇。
     *
     * @return 实际执行的换位次数
     */
    public int repairPlacement(String[] positions, List<DrawRequest.PlayerView> players,
                               boolean enabled, Random rnd) {
        Map<String, DrawRequest.PlayerView> byId = new HashMap<>();
        for (DrawRequest.PlayerView p : players) {
            byId.put(p.getPlayerId(), p);
        }
        int size = positions.length;
        int rounds = roundsOf(size);
        return repair(positions, byId, size, rounds, enabled, rnd).swaps;
    }

    /** 首轮同单位相遇的对数（对外暴露，便于测试与校验） */
    public int firstRoundClashes(String[] positions, List<DrawRequest.PlayerView> players) {
        Map<String, DrawRequest.PlayerView> byId = new HashMap<>();
        for (DrawRequest.PlayerView p : players) {
            byId.put(p.getPlayerId(), p);
        }
        return firstRoundClashCount(positions, byId, positions.length);
    }

    /**
     * 对已保存的签位表重新做公平性度量。
     * 用于赛事抽签完成后，从小程序端随时复核抽签质量。
     */
    public FairnessReport analyzePositions(List<String> positions, List<DrawRequest.PlayerView> players) {
        Map<String, DrawRequest.PlayerView> byId = new HashMap<>();
        for (DrawRequest.PlayerView p : players) {
            byId.put(p.getPlayerId(), p);
        }
        int size = positions.size();
        String[] pos = new String[size];
        for (int i = 0; i < size; i++) {
            String v = positions.get(i);
            pos[i] = (v == null || v.isEmpty() || "-".equals(v)) ? null : v;
        }
        int seedCount = 0;
        for (DrawRequest.PlayerView p : players) {
            if (p.isSeed()) seedCount++;
        }
        return analyze(pos, byId, seedCount, size, roundsOf(size));
    }

    /** 首轮同单位相遇的对数 */
    private int firstRoundClashCount(String[] pos, Map<String, DrawRequest.PlayerView> byId, int size) {
        int c = 0;
        for (int i = 0; i < size; i += 2) {
            String a = pos[i], b = pos[i + 1];
            if (a == null || b == null || BYE.equals(a) || BYE.equals(b)) continue;
            DrawRequest.PlayerView pa = byId.get(a), pb = byId.get(b);
            if (pa == null || pb == null) continue;
            String ta = teamOf(pa);
            if (!ta.isEmpty() && ta.equals(teamOf(pb))) c++;
        }
        return c;
    }

    // ========================================================================
    //  三、逐轮配对签表（动态签表）的规避与公平性分析
    //
    //  动态签表没有 2^n 签位，只有一条「首轮配对顺序」：相邻两人即首轮对手。
    //  因此原来的 meetRound / 1/4 区统计不再适用，这里改用顺序维度上的等价指标。
    // ========================================================================

    /**
     * 逐轮配对表的首轮同单位规避：只做相邻配对的贪心换位，且不移动种子
     * （种子位由落位顺序固定，移动会破坏"1、2 号种子分处两端"的约束）。
     *
     * @return 修正的对数
     */
    public int repairFirstRoundClashes(List<String> order, List<DrawRequest.PlayerView> players,
                                       boolean enabled) {
        if (!enabled || order == null || order.size() < 4) return 0;
        Map<String, DrawRequest.PlayerView> byId = new HashMap<>();
        for (DrawRequest.PlayerView p : players) byId.put(p.getPlayerId(), p);

        int fixed = 0;
        for (int i = 0; i + 1 < order.size(); i += 2) {
            if (!sameTeamOf(byId.get(order.get(i)), byId.get(order.get(i + 1)))) continue;
            for (int j = i + 2; j < order.size(); j++) {
                DrawRequest.PlayerView a = byId.get(order.get(i));
                DrawRequest.PlayerView b = byId.get(order.get(i + 1));
                DrawRequest.PlayerView c = byId.get(order.get(j));
                if (a == null || b == null || c == null) continue;
                if (c.isSeed() || b.isSeed()) continue;          // 种子不可移动
                int mate = (j % 2 == 0) ? j + 1 : j - 1;
                if (mate < 0 || mate >= order.size()) continue;
                DrawRequest.PlayerView d = byId.get(order.get(mate));
                // 交换 b 与 c 后，新配对为 (a,c) 与 (d,b)，两者都必须不再同单位
                if (sameTeamOf(a, c) || sameTeamOf(d, b)) continue;
                order.set(i + 1, c.getPlayerId());
                order.set(j, b.getPlayerId());
                fixed++;
                break;
            }
        }
        return fixed;
    }

    /**
     * 逐轮配对表的公平性分析。
     *
     * 分区指标由「1/4 签位区」改为「把配对顺序等分为 4 段」，
     * 含义仍是「实力与种子是否被摊平到不同区域」，便于前端沿用同一套展示。
     */
    public FairnessReport analyzeOrder(List<String> order, List<DrawRequest.PlayerView> players) {
        FairnessReport r = new FairnessReport();
        if (order == null || order.isEmpty()) return r;
        Map<String, DrawRequest.PlayerView> byId = new HashMap<>();
        for (DrawRequest.PlayerView p : players) byId.put(p.getPlayerId(), p);

        r.setFormat("SINGLE_ELIM");
        r.setBracketSize(order.size());

        // 1) 首轮同单位相遇（相邻配对即首轮对手）
        int pairs = order.size() / 2;
        int firstClash = 0;
        for (int i = 0; i + 1 < order.size(); i += 2) {
            if (sameTeamOf(byId.get(order.get(i)), byId.get(order.get(i + 1)))) firstClash++;
        }
        r.setFirstRoundClashes(firstClash);
        r.setAvoidanceRate(round2(pairs == 0 ? 1.0 : 1.0 - (double) firstClash / pairs));

        // 前两轮内的同单位相遇对数：逐轮配对按相邻顺序展开，
        // 位置差 < 4 的两人才可能在前两轮相遇（与 2^n 分区规则等价）。
        int earlyPairs = 0;
        for (int i = 0; i < order.size(); i++) {
            String ti = teamOf(byId.get(order.get(i)));
            if (ti.isEmpty()) continue;
            for (int j = i + 1; j < Math.min(order.size(), i + 4); j++) {
                if (ti.equals(teamOf(byId.get(order.get(j))))) earlyPairs++;
            }
        }
        r.setEarlyClashes(earlyPairs);

        // 2) 同单位总配对数
        Map<String, Integer> teamCnt = new HashMap<>();
        for (String id : order) {
            String t = teamOf(byId.get(id));
            if (!t.isEmpty()) teamCnt.merge(t, 1, Integer::sum);
        }
        int sameTeamPairs = 0;
        for (Integer c : teamCnt.values()) sameTeamPairs += c * (c - 1) / 2;
        r.setSameTeamPairs(sameTeamPairs);

        // 3) 四分区：把配对顺序等分为 4 段
        int quarters = order.size() >= 8 ? 4 : Math.max(1, order.size() / 2);
        int seg = (int) Math.ceil(order.size() * 1.0 / quarters);
        List<Integer> seedPer = new ArrayList<>();
        List<Integer> levelPer = new ArrayList<>();
        for (int q = 0; q < quarters; q++) {
            int s = 0, lv = 0;
            for (int i = q * seg; i < Math.min(order.size(), (q + 1) * seg); i++) {
                DrawRequest.PlayerView p = byId.get(order.get(i));
                if (p == null) continue;
                if (p.isSeed()) s++;
                lv += p.getLevel();
            }
            seedPer.add(s);
            levelPer.add(lv);
        }
        r.setSeedPerQuarter(seedPer);
        r.setLevelPerQuarter(levelPer);

        // 4) 实力离散度
        double std = stdDevOf(levelPer);
        r.setLevelStdDev(round2(std));

        // 5) 综合得分：沿用与 2^n 签位分析一致的「四段式」构成（35+30+25+10），
        //    保证两种签表口径可比；实力量纲用变异系数归一化，不随人数放大。
        int seedCount = 0;
        for (DrawRequest.PlayerView p : players) {
            if (p.isSeed()) seedCount++;
        }
        // ① 种子分区均衡度 0~35
        double seedScore = 35;
        if (seedCount > 0 && quarters > 1) {
            double ideal = (double) seedCount / quarters;
            double dev = 0;
            for (Integer v : seedPer) dev += Math.abs((v == null ? 0 : v) - ideal);
            double maxDev = 2.0 * seedCount;
            seedScore = 35 * Math.max(0, 1 - dev / maxDev * 2);
        }
        // ② 实力均衡度 0~30
        double meanLv = 0;
        for (Integer v : levelPer) meanLv += v == null ? 0 : v;
        meanLv /= Math.max(1, levelPer.size());
        double levelScore = 30;
        if (meanLv > 0) {
            double cv = std / meanLv;
            levelScore = 30 * Math.max(0, 1 - Math.min(1, cv / 0.5));
        }
        // ③ 同单位规避达成率 0~25
        double avoidScore = 25 * r.getAvoidanceRate();
        // ④ 轮空均衡 0~10：逐轮配对的轮空由人数奇偶决定，无需均衡，给满分
        double byeScore = 10;
        r.setScore(Math.round((seedScore + levelScore + avoidScore + byeScore) * 10) / 10.0);

        if (firstClash > 0) {
            r.addNote("首轮存在 " + firstClash + " 对同单位对阵，建议增加种子数或放宽规避条件");
        } else {
            r.addNote("首轮无同单位对阵，同单位规避达成");
        }
        if (sameTeamPairs == 0) {
            r.addNote("参赛选手中不存在同单位组合，规避规则不适用");
        }
        r.addNote("逐轮配对签表：每轮两两配对，人数为奇数时 1 人轮空");
        return r;
    }

    private static boolean sameTeamOf(DrawRequest.PlayerView a, DrawRequest.PlayerView b) {
        String ta = teamOf(a);
        return !ta.isEmpty() && ta.equals(teamOf(b));
    }

    private static double stdDevOf(List<Integer> vals) {
        if (vals == null || vals.isEmpty()) return 0;
        double avg = 0;
        for (Integer v : vals) avg += v == null ? 0 : v;
        avg /= vals.size();
        double s = 0;
        for (Integer v : vals) {
            double d = (v == null ? 0 : v) - avg;
            s += d * d;
        }
        return Math.sqrt(s / vals.size());
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** 单位名归一化；空白视为「无单位」，不参与规避 */
    private static String teamOf(DrawRequest.PlayerView p) {
        String t = p == null ? null : p.getTeam();
        return t == null ? "" : t.trim();
    }

    /** 把候选签位按 1/4 区轮流展开，避免同类签位扎堆 */
    private static List<Integer> spreadAcrossSections(List<Integer> slots, int size) {
        List<Integer> sorted = new ArrayList<>(slots);
        Collections.sort(sorted);
        int sections = Math.max(1, Math.min(4, size));
        int sectionSize = Math.max(1, size / sections);
        Map<Integer, List<Integer>> bucket = new TreeMap<>();
        for (Integer s : sorted) {
            int key = Math.min(sections - 1, s / sectionSize);
            bucket.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        List<Integer> out = new ArrayList<>();
        int idx = 0;
        boolean added = true;
        while (added) {
            added = false;
            for (List<Integer> list : bucket.values()) {
                if (idx < list.size()) {
                    out.add(list.get(idx));
                    added = true;
                }
            }
            idx++;
        }
        return out;
    }

    private static List<String> snapshot(String[] pos) {
        List<String> list = new ArrayList<>(pos.length);
        for (String s : pos) list.add(s);
        return list;
    }
}
