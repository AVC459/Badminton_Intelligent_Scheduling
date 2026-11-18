package com.ape.badminton.algorithm;

import com.ape.badminton.entity.Match;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编排算法单元测试
 *
 * 覆盖项目方案中的四项关键算法与多赛制支持能力，是「算法正确性」的工程化验证。
 */
class DrawEngineTest {

    private final DrawEngine engine = new DrawEngine();

    // ========================================================================
    //  一、基础工具
    // ========================================================================

    @Test
    @DisplayName("签位规模取不小于人数的 2 的幂")
    void testBracketSize() {
        assertEquals(1, DrawEngine.bracketSize(1));
        assertEquals(2, DrawEngine.bracketSize(2));
        assertEquals(8, DrawEngine.bracketSize(5));
        assertEquals(8, DrawEngine.bracketSize(8));
        assertEquals(16, DrawEngine.bracketSize(9));
        assertEquals(64, DrawEngine.bracketSize(64));
    }

    @Test
    @DisplayName("蛇形落位顺序表符合标准签表")
    void testSeedingOrder() {
        // 1 号种子固定在首位，2 号种子固定在末位
        assertArrayEqualsInt(new int[]{1, 2}, DrawEngine.seedingOrder(2));
        assertArrayEqualsInt(new int[]{1, 4, 3, 2}, DrawEngine.seedingOrder(4));
        assertArrayEqualsInt(new int[]{1, 8, 5, 4, 3, 6, 7, 2}, DrawEngine.seedingOrder(8));
        assertArrayEqualsInt(new int[]{1, 16, 9, 8, 5, 12, 13, 4, 3, 14, 11, 6, 7, 10, 15, 2},
                DrawEngine.seedingOrder(16));
    }

    @Test
    @DisplayName("最早相遇轮次计算正确")
    void testMeetRound() {
        // 相邻签位首轮相遇
        assertEquals(1, DrawEngine.meetRound(0, 1, 8));
        assertEquals(1, DrawEngine.meetRound(6, 7, 8));
        // 同一 1/4 区次轮相遇
        assertEquals(2, DrawEngine.meetRound(0, 3, 8));
        // 不同半区决赛相遇
        assertEquals(3, DrawEngine.meetRound(0, 7, 8));
        assertEquals(4, DrawEngine.meetRound(0, 15, 16));
    }

    // ========================================================================
    //  二、种子分区（1/4 区、1/8 区避让）
    // ========================================================================

    @Test
    @DisplayName("4 名种子均匀分处 4 个 1/4 区，1、2 号种子分处上下半区")
    void testSeedDistribution() {
        List<DrawRequest.PlayerView> players = players(16, 4, 4);
        DrawResult r = engine.draw(request(players, true, 1L));
        String[] pos = toArray(r.getDrawPositions());

        Map<String, Integer> slotOf = new HashMap<>();
        for (int i = 0; i < pos.length; i++) {
            if (pos[i] != null) slotOf.put(pos[i], i);
        }
        // 1 号种子在上半区顶部，2 号种子在下半区底部
        assertEquals(0, slotOf.get("p1").intValue());
        assertEquals(15, slotOf.get("p2").intValue());
        // 4 名种子分处 4 个不同的 1/4 区
        Set<Integer> quarters = new HashSet<>();
        for (int i = 1; i <= 4; i++) {
            quarters.add(slotOf.get("p" + i) / 4);
        }
        assertEquals(4, quarters.size(), "4 名种子应分布在 4 个不同的 1/4 区");
        // 1、2 号种子分处不同半区
        assertTrue(slotOf.get("p1") < 8 && slotOf.get("p2") >= 8);
    }

    @Test
    @DisplayName("8 名种子均匀分处 8 个 1/8 区")
    void testEightSeedsDistribution() {
        List<DrawRequest.PlayerView> players = players(16, 8, 8);
        DrawResult r = engine.draw(request(players, true, 2L));
        String[] pos = toArray(r.getDrawPositions());
        Map<String, Integer> slotOf = new HashMap<>();
        for (int i = 0; i < pos.length; i++) {
            if (pos[i] != null) slotOf.put(pos[i], i);
        }
        Set<Integer> eighths = new HashSet<>();
        for (int i = 1; i <= 8; i++) {
            eighths.add(slotOf.get("p" + i) / 2);
        }
        assertEquals(8, eighths.size(), "8 名种子应分布在 8 个不同的 1/8 区");
    }

    // ========================================================================
    //  三、轮空
    // ========================================================================

    @Test
    @DisplayName("轮空数量正确且优先分配给种子选手")
    void testByesGoToSeeds() {
        // 12 人 → 16 签位 → 4 个轮空
        List<DrawRequest.PlayerView> players = players(12, 4, 4);
        DrawResult r = engine.draw(request(players, true, 3L));
        assertEquals(16, r.getBracketSize());
        assertEquals(4, r.getByeCount());

        String[] pos = toArray(r.getDrawPositions());
        long byeCount = Arrays.stream(pos).filter(DrawEngine.BYE::equals).count();
        assertEquals(4, byeCount);

        // 4 名种子的首轮对手应全部是轮空
        int seedWithBye = 0;
        for (int i = 0; i < pos.length; i += 2) {
            boolean aBye = DrawEngine.BYE.equals(pos[i]);
            boolean bBye = DrawEngine.BYE.equals(pos[i + 1]);
            if (aBye ^ bBye) {
                String other = aBye ? pos[i + 1] : pos[i];
                if (other != null && other.matches("p[1-4]")) seedWithBye++;
            }
        }
        assertEquals(4, seedWithBye, "4 名种子都应获得首轮轮空");
    }

    // ========================================================================
    //  四、同单位规避（含死签回溯修正）
    // ========================================================================

    @Test
    @DisplayName("同单位规避：人数充足时首轮无同单位相遇")
    void testTeamAvoidance() {
        // 16 人，4 个单位各 4 人 → 每单位 4 人可分处 4 个 1/4 区，首轮必不同组
        List<DrawRequest.PlayerView> players = players(16, 4, 4);
        DrawResult r = engine.draw(request(players, true, 7L));
        String[] pos = toArray(r.getDrawPositions());
        assertEquals(0, engine.firstRoundClashes(pos, players),
                "16 人 4 单位，应可完全规避首轮同单位相遇");
        assertTrue(r.isAvoidanceResolved());
        assertEquals(1.0, r.getFairness().getAvoidanceRate(), 0.0001);
    }

    @Test
    @DisplayName("同单位规避：固定随机种子时抽签结果可复现")
    void testDeterministicWithSeed() {
        List<DrawRequest.PlayerView> p1 = players(16, 4, 4);
        List<DrawRequest.PlayerView> p2 = players(16, 4, 4);
        DrawResult a = engine.draw(request(p1, true, 20260922L));
        DrawResult b = engine.draw(request(p2, true, 20260922L));
        assertEquals(a.getDrawPositions(), b.getDrawPositions(), "相同随机种子应得到相同签位");
    }

    @Test
    @DisplayName("同单位规避：多家单位随机 200 次，首轮冲突率应极低")
    void testAvoidanceAcrossManyDraws() {
        int totalFirstRoundClashes = 0;
        int draws = 200;
        for (long seed = 0; seed < draws; seed++) {
            List<DrawRequest.PlayerView> players = players(32, 8, 8);
            DrawResult r = engine.draw(request(players, true, seed));
            totalFirstRoundClashes += engine.firstRoundClashes(toArray(r.getDrawPositions()), players);
        }
        assertEquals(0, totalFirstRoundClashes,
                "32 人 8 个单位各 4 人，理论上可完全规避首轮同单位相遇；200 次抽签均不应出现冲突");
    }

    @Test
    @DisplayName("同单位规避：单位人数超出分区上限时给出风险提示而非崩溃")
    void testUnavoidableCaseReported() {
        // 8 签位里 5 人同属一个单位 → 必然有两人同处一个 1/4 区
        List<DrawRequest.PlayerView> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(new DrawRequest.PlayerView("p" + i, "同队" + i, "计算机学院", 80, false, 0));
        }
        for (int i = 6; i <= 8; i++) {
            players.add(new DrawRequest.PlayerView("p" + i, "其他" + i, "体育学院", 80, false, 0));
        }
        DrawResult r = engine.draw(request(players, true, 9L));
        assertNotNull(r.getFairness());
        assertFalse(r.getFairness().getNotes().isEmpty(), "无法完全规避时应给出风险提示");
        // 8 签位、5 人同队，理论最优是首轮仅有 1 处冲突（无法做到 0）
        assertTrue(engine.firstRoundClashes(toArray(r.getDrawPositions()), players) <= 1);
    }

    // ========================================================================
    //  五、淘汰赛对阵树
    // ========================================================================

    @Test
    @DisplayName("淘汰赛对阵树：场次数、晋级链、轮空自动晋级均正确")
    void testBracketTree() {
        List<DrawRequest.PlayerView> players = players(12, 4, 4);
        DrawResult r = engine.draw(request(players, true, 11L));
        String prefix = "t1K";
        List<Match> matches = BracketBuilder.buildKnockout("t1", prefix,
                r.getDrawPositions(), BracketBuilder.STAGE_KNOCKOUT, true);

        // 16 签位 → 15 场淘汰赛 + 1 场季军赛
        assertEquals(16, matches.size());
        // 淘汰赛阶段：首轮 8 场，1/4 决赛 4 场，半决赛 2 场，决赛 1 场（共 15 场）
        assertEquals(8, countKnockoutRound(matches, 1));
        assertEquals(4, countKnockoutRound(matches, 2));
        assertEquals(2, countKnockoutRound(matches, 3));
        assertEquals(1, countKnockoutRound(matches, 4));
        assertEquals(15, matches.stream()
                .filter(m -> BracketBuilder.STAGE_KNOCKOUT.equals(m.getStage())).count());
        // 季军赛独立成阶段：1 场，且与决赛同属最后一轮，但阶段标识必须区分
        assertEquals(1, matches.stream().filter(m -> BracketBuilder.STAGE_THIRD.equals(m.getStage())).count());
        Match third = matches.stream()
                .filter(m -> BracketBuilder.STAGE_THIRD.equals(m.getStage())).findFirst().orElse(null);
        assertNotNull(third, "应生成季军赛");
        assertEquals(Integer.valueOf(4), third.getRound(), "季军赛与决赛同轮");
        assertEquals("季军赛", third.getRound_name());

        // 轮空场次自动判胜并晋级
        for (Match m : matches) {
            if (m.getRound() == 1 && BracketBuilder.STATUS_DONE.equals(m.getStatus())) {
                assertNotNull(m.getWinner_id(), "轮空场次应自动产生胜方");
                assertNotNull(m.getNext_match_id());
            }
            // 淘汰赛除决赛外都应配置晋级目标；季军赛是终点，无晋级目标
            if (m.getRound() > 1 && BracketBuilder.STAGE_KNOCKOUT.equals(m.getStage())
                    && !"决赛".equals(m.getRound_name())) {
                assertNotNull(m.getNext_match_id(), "非决赛场次都应配置晋级目标");
                assertNotNull(m.getNext_slot());
            }
        }
        // 决赛没有晋级目标
        for (Match m : matches) {
            if ("决赛".equals(m.getRound_name()) && BracketBuilder.STAGE_KNOCKOUT.equals(m.getStage())) {
                assertEquals(null, m.getNext_match_id());
            }
        }
        // 半决赛负方应进入季军赛
        for (Match m : matches) {
            if ("半决赛".equals(m.getRound_name())) {
                assertNotNull(m.getLoser_next_id(), "半决赛负方应进入季军赛");
            }
        }
    }

    @Test
    @DisplayName("对阵树场次ID全局唯一（季军赛不得与决赛撞号）")
    void testMatchIdUnique() {
        // 回归：季军赛与决赛同轮次，早期实现复用 prefix_R{末轮}_1 导致主键冲突
        for (int n : new int[]{4, 5, 8, 9, 12, 16, 17}) {
            List<DrawRequest.PlayerView> players = players(n, 4, 4);
            DrawResult r = engine.draw(request(players, true, 42L));
            for (boolean third : new boolean[]{true, false}) {
                List<Match> matches = BracketBuilder.buildKnockout("t1", "t1K",
                        r.getDrawPositions(), BracketBuilder.STAGE_KNOCKOUT, third);
                Set<String> ids = new HashSet<>();
                for (Match m : matches) {
                    assertTrue(ids.add(m.getMatch_id()),
                            "n=" + n + " third=" + third + " 场次ID重复：" + m.getMatch_id());
                }
                if (third) {
                    assertTrue(matches.stream().anyMatch(m -> BracketBuilder.STAGE_THIRD.equals(m.getStage())),
                            "n=" + n + " 应生成季军赛");
                }
            }
        }
    }

    @Test
    @DisplayName("轮次名称与签位规模匹配")
    void testRoundName() {        assertEquals("决赛", BracketBuilder.roundName(4, 4));
        assertEquals("半决赛", BracketBuilder.roundName(3, 4));
        assertEquals("1/4决赛", BracketBuilder.roundName(2, 4));
        assertEquals("1/8决赛", BracketBuilder.roundName(1, 4));
    }

    // ========================================================================
    //  六、单循环赛
    // ========================================================================

    @Test
    @DisplayName("单循环赛：任意两人恰好相遇一次，轮次安排无重复")
    void testRoundRobin() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 7; i++) ids.add("p" + i);   // 奇数人数，触发轮空
        List<Match> matches = RoundRobinEngine.build("t1", "t1L", ids,
                RoundRobinEngine.STAGE_ROUND_ROBIN, 0, 1);

        // 7 人 → 7 轮，每轮 3 场 → 21 场
        assertEquals(21, matches.size());

        Set<String> pairs = new HashSet<>();
        Map<Integer, Set<String>> busyPerRound = new HashMap<>();
        for (Match m : matches) {
            String key = m.getPlayer_a_id().compareTo(m.getPlayer_b_id()) < 0
                    ? m.getPlayer_a_id() + "-" + m.getPlayer_b_id()
                    : m.getPlayer_b_id() + "-" + m.getPlayer_a_id();
            assertTrue(pairs.add(key), "同一对选手不应重复对阵：" + key);
            Set<String> busy = busyPerRound.computeIfAbsent(m.getRound(), k -> new HashSet<>());
            assertTrue(busy.add(m.getPlayer_a_id()), "同一轮内选手重复出场：" + m.getPlayer_a_id());
            assertTrue(busy.add(m.getPlayer_b_id()), "同一轮内选手重复出场：" + m.getPlayer_b_id());
        }
        assertEquals(21, pairs.size(), "7 人两两组合应有 21 种不重复对阵");
    }

    @Test
    @DisplayName("循环赛排名：积分 → 净胜局 → 净胜分 依次比较")
    void testStandings() {
        List<String> ids = Arrays.asList("a", "b", "c");
        Map<String, String> names = new HashMap<>();
        names.put("a", "甲");
        names.put("b", "乙");
        names.put("c", "丙");

        List<Match> matches = new ArrayList<>();
        matches.add(doneMatch("a", "b", 2, 0, 42, 25));
        matches.add(doneMatch("a", "c", 2, 1, 55, 48));
        matches.add(doneMatch("b", "c", 2, 0, 42, 30));

        List<Standing> st = RoundRobinEngine.standings(ids, matches, names, new HashMap<>());
        assertEquals(3, st.size());
        assertEquals("a", st.get(0).getPlayerId(), "两战全胜者应排第一");
        assertEquals(4, st.get(0).getPoints());
        assertEquals(2, st.get(0).getWon());
        assertEquals("b", st.get(1).getPlayerId());
        assertEquals("c", st.get(2).getPlayerId());
    }

    // ========================================================================
    //  七、小组循环 + 交叉淘汰
    // ========================================================================

    @Test
    @DisplayName("蛇形分组：各组人数均衡且种子分散")
    void testSnakeAssign() {
        List<DrawRequest.PlayerView> players = players(12, 4, 4);
        List<List<DrawRequest.PlayerView>> groups = GroupKnockoutEngine.snakeAssign(players, 4, 5L);
        assertEquals(4, groups.size());
        for (List<DrawRequest.PlayerView> g : groups) {
            assertEquals(3, g.size(), "12 人分 4 组，每组应为 3 人");
        }
        // 前 4 名种子应分处 4 个不同小组
        Set<Integer> seedGroups = new HashSet<>();
        for (int gi = 0; gi < groups.size(); gi++) {
            for (DrawRequest.PlayerView p : groups.get(gi)) {
                if (p.getSeedRank() >= 1 && p.getSeedRank() <= 4) seedGroups.add(gi);
            }
        }
        assertEquals(4, seedGroups.size(), "4 名种子应分散到 4 个小组");
    }

    @Test
    @DisplayName("交叉淘汰：同组出线选手最早只能在决赛相遇")
    void testCrossKnockout() {
        // 4 组，每组出线 2 人 → 8 签位
        List<List<String>> qualified = Arrays.asList(
                Arrays.asList("A1", "A2"),
                Arrays.asList("B1", "B2"),
                Arrays.asList("C1", "C2"),
                Arrays.asList("D1", "D2"));
        String[] pos = GroupKnockoutEngine.crossKnockoutPositions(qualified, 2, 8);
        Map<String, Integer> slotOf = new HashMap<>();
        for (int i = 0; i < pos.length; i++) {
            if (pos[i] != null && !DrawEngine.BYE.equals(pos[i])) slotOf.put(pos[i], i);
        }
        assertEquals(8, slotOf.size());
        int finalRound = DrawEngine.roundsOf(8);
        // 同组的两名出线选手不应在首轮（同一签位对）相遇
        String[][] sameGroups = {{"A1", "A2"}, {"B1", "B2"}, {"C1", "C2"}, {"D1", "D2"}};
        for (String[] pair : sameGroups) {
            int s1 = slotOf.get(pair[0]);
            int s2 = slotOf.get(pair[1]);
            assertTrue(s1 / 2 != s2 / 2, pair[0] + " 与 " + pair[1] + " 不应首轮相遇");
            // 标准交叉表下，同组两人应分处上下半区，最早决赛相遇
            assertEquals(finalRound, DrawEngine.meetRound(Math.min(s1, s2), Math.max(s1, s2), 8),
                    pair[0] + " 与 " + pair[1] + " 应分处上下半区，最早决赛相遇");
        }
    }

    @Test
    @DisplayName("交叉淘汰：多种组数/出线人数组合下同组首轮均不相遇")
    void testCrossKnockoutAllCombinations() {
        // 覆盖 2/4/8 组 × 出线 1/2 人，并夹杂不同人数，验证构造的普适性
        int[][] combos = {{2, 1}, {2, 2}, {4, 1}, {4, 2}, {8, 1}, {8, 2}};
        for (int[] combo : combos) {
            int g = combo[0];
            int advance = combo[1];
            int qualifiers = g * advance;
            int size = DrawEngine.bracketSize(qualifiers);

            List<List<String>> qualified = new ArrayList<>();
            for (int gi = 0; gi < g; gi++) {
                List<String> grp = new ArrayList<>();
                for (int j = 0; j < advance; j++) {
                    grp.add("G" + gi + "_" + j);
                }
                qualified.add(grp);
            }

            String[] pos = GroupKnockoutEngine.crossKnockoutPositions(qualified, advance, size);
            Map<String, Integer> slotOf = new HashMap<>();
            for (int i = 0; i < pos.length; i++) {
                if (pos[i] != null && !DrawEngine.BYE.equals(pos[i])) slotOf.put(pos[i], i);
            }
            assertEquals(qualifiers, slotOf.size(), g + "组×" + advance + "人 落位数量不符");

            for (int gi = 0; gi < g; gi++) {
                for (int j = 0; j < advance; j++) {
                    for (int k = j + 1; k < advance; k++) {
                        int s1 = slotOf.get("G" + gi + "_" + j);
                        int s2 = slotOf.get("G" + gi + "_" + k);
                        assertTrue(s1 / 2 != s2 / 2,
                                g + "组×" + advance + "人：同组 " + gi + " 的首轮对阵冲突");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("交叉淘汰：同单位修正不得破坏同组分散")
    void testRepairKnockoutKeepsGroupSeparation() {
        // 构造一个「组分散正确、但首轮存在同单位对阵」的签表：
        // 让 A1 与 B1 同单位（恰好是首轮对手）
        List<List<String>> qualified = Arrays.asList(
                Arrays.asList("A1", "A2"),
                Arrays.asList("B1", "B2"),
                Arrays.asList("C1", "C2"),
                Arrays.asList("D1", "D2"));
        String[] pos = GroupKnockoutEngine.crossKnockoutPositions(qualified, 2, 8);

        Map<String, String> teamOf = new HashMap<>();
        Map<String, String> groupOf = new HashMap<>();
        for (int gi = 0; gi < qualified.size(); gi++) {
            for (int j = 0; j < qualified.get(gi).size(); j++) {
                String id = qualified.get(gi).get(j);
                groupOf.put(id, "G" + gi);
                teamOf.put(id, "TEAM_" + gi);   // 每组一个单位，默认无同单位
            }
        }
        // 制造冲突：把 A1 与它的首轮对手设为同一单位
        int slotA1 = indexOf(pos, "A1");
        int oppA = (slotA1 % 2 == 0) ? slotA1 + 1 : slotA1 - 1;
        String opp = pos[oppA];
        teamOf.put("A1", "SAME");
        teamOf.put(opp, "SAME");

        int repairs = GroupKnockoutEngine.repairKnockoutClashes(pos, teamOf, groupOf, 8);
        assertTrue(repairs >= 1, "存在同单位首轮对阵时应至少完成一次修正");

        // 修复后：首轮不得再出现同单位，且同组分散必须保持
        for (int p = 0; p < 4; p++) {
            String a = pos[p * 2];
            String b = pos[p * 2 + 1];
            if (DrawEngine.BYE.equals(a) || DrawEngine.BYE.equals(b)) continue;
            assertNotEquals(teamOf.get(a), teamOf.get(b), "首轮仍有同单位对阵：" + a + " vs " + b);
            assertNotEquals(groupOf.get(a), groupOf.get(b), "首轮出现同组对阵：" + a + " vs " + b);
        }
        for (String[] pair : new String[][]{{"A1", "A2"}, {"B1", "B2"}, {"C1", "C2"}, {"D1", "D2"}}) {
            int s1 = indexOf(pos, pair[0]);
            int s2 = indexOf(pos, pair[1]);
            assertEquals(3, DrawEngine.meetRound(Math.min(s1, s2), Math.max(s1, s2), 8),
                    pair[0] + " 与 " + pair[1] + " 应仍分处上下半区");
        }
    }

    private int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (v.equals(arr[i])) return i;
        }
        return -1;
    }

    // ========================================================================
    //  测试辅助
    // ========================================================================

    /** 构造 n 名选手，其中 seedCount 名为种子，人员均匀分布在 teamCount 个单位 */
    private List<DrawRequest.PlayerView> players(int n, int seedCount, int teamCount) {
        List<DrawRequest.PlayerView> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String team = "单位" + ((i - 1) % teamCount + 1);
            boolean seed = i <= seedCount;
            int rank = seed ? i : 0;
            list.add(new DrawRequest.PlayerView("p" + i, "选手" + i, team, 100 - i, seed, rank));
        }
        return list;
    }

    private DrawRequest request(List<DrawRequest.PlayerView> players, boolean avoid, Long seed) {
        DrawRequest req = new DrawRequest();
        req.setPlayers(players);
        req.setAvoidSameTeam(avoid);
        req.setRandomSeed(seed);
        return req;
    }

    private String[] toArray(List<String> list) {
        return list.toArray(new String[0]);
    }

    private void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "第 " + i + " 位不一致");
        }
    }

    private long countRound(List<Match> matches, int round) {
        return matches.stream().filter(m -> m.getRound() != null && m.getRound() == round).count();
    }

    /** 仅统计淘汰赛（不含季军赛）指定轮次的场次数 */
    private long countKnockoutRound(List<Match> matches, int round) {
        return matches.stream()
                .filter(m -> BracketBuilder.STAGE_KNOCKOUT.equals(m.getStage()))
                .filter(m -> m.getRound() != null && m.getRound() == round)
                .count();
    }

    private Match doneMatch(String a, String b, int ga, int gb, int pa, int pb) {
        Match m = new Match();
        m.setMatch_id("m_" + a + "_" + b);
        m.setPlayer_a_id(a);
        m.setPlayer_b_id(b);
        m.setGames_a(ga);
        m.setGames_b(gb);
        m.setScore_a(pa);
        m.setScore_b(pb);
        m.setWinner_id(ga > gb ? a : b);
        m.setLoser_id(ga > gb ? b : a);
        m.setStatus(BracketBuilder.STATUS_DONE);
        return m;
    }

}
