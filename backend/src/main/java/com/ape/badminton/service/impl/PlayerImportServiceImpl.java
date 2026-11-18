package com.ape.badminton.service.impl;

import com.ape.badminton.service.OllamaClient;
import com.ape.badminton.service.PlayerImportService;
import com.ape.badminton.utils.ExcelParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 名单导入实现
 *
 * 解析链路：
 *    Excel / 文本  →  二维字符串数组
 *                  →  表头识别（别名映射）
 *                  →  规则解析（兜底，永远可用）
 *                  →  本地 Ollama 语义识别（可选增强，失败自动降级）
 *                  →  合并输出统一字段的选手列表
 *
 * 输出字段与前端、数据库完全一致：
 *   student_id / name / gender(M|F) / team / phone / level / is_seed / seed_rank
 */
@Service
public class PlayerImportServiceImpl implements PlayerImportService {

    private static final Logger log = LoggerFactory.getLogger(PlayerImportServiceImpl.class);

    /** 11 位手机号 */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    /** 纯数字学号（4 位以上） */
    private static final Pattern NUMERIC_ID = Pattern.compile("^\\d{4,}$");
    /** 中文姓名：2-4 个汉字，允许维吾尔族等间隔点 */
    private static final Pattern CN_NAME = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,6}$");
    /** 单位关键字 */
    private static final Pattern TEAM_HINT = Pattern.compile("(学院|大学|学部|学系|部门|公司|中心|研究院|俱乐部|协会|年级|班级|联队|队)");

    /** 双打组合分隔符：报名表常把一对组合写在同一格里，如「张三/李四」 */
    private static final Pattern PAIR_SEP = Pattern.compile("[/、＋+&＆,，;；]");

    private static final Map<String, String> HEADER_ALIAS = new LinkedHashMap<>();

    /** 五个分支及其显示名（同时也是「参赛类别」列可以直写的代码） */
    private static final Map<String, String> CATEGORY_LABEL = new LinkedHashMap<>();

    /**
     * 「参赛类别」列的常见写法 → 分支代码。
     *
     * 长写法必须放在短写法前面，否则「男子单打」会被先匹配到别的规则上；
     * 认不出来的写法一律不导入并给出原因，绝不猜分支。
     */
    private static final Map<String, String> CATEGORY_ALIAS = new LinkedHashMap<>();

    static {
        CATEGORY_LABEL.put("MS", "男单");
        CATEGORY_LABEL.put("WS", "女单");
        CATEGORY_LABEL.put("MD", "男双");
        CATEGORY_LABEL.put("WD", "女双");
        CATEGORY_LABEL.put("XD", "混双");

        CATEGORY_ALIAS.put("男子单打", "MS");
        CATEGORY_ALIAS.put("女子单打", "WS");
        CATEGORY_ALIAS.put("男子双打", "MD");
        CATEGORY_ALIAS.put("女子双打", "WD");
        CATEGORY_ALIAS.put("混合双打", "XD");
        CATEGORY_ALIAS.put("男单", "MS");
        CATEGORY_ALIAS.put("女单", "WS");
        CATEGORY_ALIAS.put("男双", "MD");
        CATEGORY_ALIAS.put("女双", "WD");
        CATEGORY_ALIAS.put("混双", "XD");
    }

    static {
        // 表头别名 → 系统字段
        alias("student_id", "学号", "学籍号", "工号", "编号", "运动员编号", "student_id", "stuid", "id", "序号", "学号1");
        alias("name", "姓名", "名字", "选手", "运动员", "参赛人", "name", "姓名1", "选手1", "队员1", "组合");
        alias("gender", "性别", "sex", "gender");
        alias("team", "单位", "学院", "院系", "部门", "所属单位", "队伍", "班级", "单位名称", "team", "参赛单位");
        alias("phone", "电话", "手机", "手机号", "联系方式", "联系电话", "phone", "mobile");
        alias("level", "水平分", "积分", "水平", "实力", "得分", "level", "rating");
        alias("seed_rank", "种子", "种子序号", "种子顺位", "seed", "seed_rank");
        // 「参赛类别」列：整份名单以这一列为准落到各自分支，系统不做任何推断
        alias("category", "参赛类别", "参赛项目", "比赛项目", "项目类型", "参赛类型",
                "category", "event", "event_type");
        // 双打组队信息
        alias("partner_name", "搭档", "搭档姓名", "选手2", "队员2", "第二队员", "配对", "partner", "partner_name");
        alias("partner_student_id", "搭档学号", "学号2", "队员2学号", "partner_id", "partner_student_id");
        // 搭档单独填单位是常见情况：混双/跨院组队时两人学院并不相同，
        // 不能默认沿用主选手的单位
        alias("partner_team", "搭档单位", "搭档学院", "队员2单位", "选手2单位", "第二单位", "partner_team");
    }

    /** 是否为双打分支 */
    static boolean isDoubles(String category) {
        String c = category == null ? "" : category.trim().toUpperCase();
        return "MD".equals(c) || "WD".equals(c) || "XD".equals(c);
    }

    /** 是否为系统认可的五个分支之一 */
    private static boolean isKnownCategory(String category) {
        return category != null && CATEGORY_LABEL.containsKey(category.trim().toUpperCase());
    }

    /** 调用方是否显式指定了分支（空 / AUTO 都表示「由名单的参赛类别列自己声明」） */
    private static boolean isExplicitCategory(String category) {
        return category != null && !category.trim().isEmpty()
                && !"AUTO".equalsIgnoreCase(category.trim());
    }

    /**
     * 「参赛类别」列的取值 → 分支代码；认不出来返回空串。
     *
     * 认不出来时绝不猜一个分支顶上去 —— 猜错分支会让整份名单落到错误的地方，
     * 比「这一行不导入 + 明确报错」麻烦得多。
     */
    private static String parseCategory(String raw) {
        String s = raw == null ? "" : raw.trim().replace(" ", "").replace("　", "");
        if (s.isEmpty()) return "";
        String upper = s.toUpperCase();
        if (CATEGORY_LABEL.containsKey(upper)) return upper;   // 也允许直接写 MS/WS/MD/WD/XD
        for (Map.Entry<String, String> e : CATEGORY_ALIAS.entrySet()) {
            if (s.contains(e.getKey())) return e.getValue();
        }
        return "";
    }

    private static void alias(String field, String... names) {
        for (String n : names) {
            HEADER_ALIAS.put(n.toLowerCase(), field);
        }
    }

    @Autowired
    private OllamaClient ollama;

    /**
     * 送给模型判断「列语义」的样例行数。
     *
     * 模型只做列映射、不做逐行转写，因此几行样例就够了；
     * 识别耗时与表格总行数无关（1000 行的表同样是这几行样例）。
     */
    @Value("${ollama.max-rows:3}")
    private int maxRows;

    /** 模型输出的短键 → 系统内部字段名（同时兼容模型偶发输出完整字段名的情况） */
    private static final Map<String, String> IMPORT_FIELD = new LinkedHashMap<>();

    static {
        IMPORT_FIELD.put("s", "student_id");
        IMPORT_FIELD.put("student_id", "student_id");
        IMPORT_FIELD.put("n", "name");
        IMPORT_FIELD.put("name", "name");
        IMPORT_FIELD.put("g", "gender");
        IMPORT_FIELD.put("gender", "gender");
        IMPORT_FIELD.put("t", "team");
        IMPORT_FIELD.put("team", "team");
        IMPORT_FIELD.put("lv", "level");
        IMPORT_FIELD.put("level", "level");
        IMPORT_FIELD.put("ph", "phone");
        IMPORT_FIELD.put("phone", "phone");
        IMPORT_FIELD.put("pn", "partner_name");
        IMPORT_FIELD.put("partner_name", "partner_name");
        IMPORT_FIELD.put("ps", "partner_student_id");
        IMPORT_FIELD.put("partner_student_id", "partner_student_id");
        IMPORT_FIELD.put("pt", "partner_team");
        IMPORT_FIELD.put("partner_team", "partner_team");
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Map<String, Object> fromExcel(InputStream in, boolean useAi, String category) {
        return build(ExcelParser.readRows(in), useAi, "excel", category);
    }

    @Override
    public Map<String, Object> fromText(String text, boolean useAi, String category) {
        return build(ExcelParser.readText(text), useAi, "text", category);
    }

    @Override
    public Map<String, Object> aiStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean ok = ollama.isEnabled() && ollama.available();
        m.put("enabled", ollama.isEnabled());
        m.put("available", ok);
        m.put("model", ollama.getModel());
        m.put("message", ok ? "本地智能识别已就绪（" + ollama.getModel() + "）"
                : "未检测到本地 Ollama 服务，导入时将使用规则解析");
        return m;
    }

    // ========================================================================
    //  主流程
    // ========================================================================

    private Map<String, Object> build(List<List<String>> rows, boolean useAi, String source, String category) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("total_rows", rows.size());

        if (rows.isEmpty()) {
            out.put("category", category == null ? "" : category);
            out.put("doubles", isDoubles(category));
            out.put("players", new ArrayList<>());
            out.put("valid_count", 0);
            out.put("invalid_count", 0);
            out.put("ai_used", false);
            out.put("ai_message", "没有可解析的数据行");
            return out;
        }

        Map<String, Integer> header = detectHeader(rows.get(0));
        boolean hasHeader = !header.isEmpty();
        List<List<String>> data = hasHeader ? rows.subList(1, rows.size()) : rows;
        out.put("header_used", hasHeader);
        out.put("header", header);

        // ---- 参赛类别：只认「参赛类别」列，系统不做任何推断 ----
        //
        // 早先是按名单内容猜分支（单打/双打 + 性别分布），一旦猜错，
        // 整份名单就会落进错误的分支。现在改为由数据自己声明：
        // 每行在「参赛类别」列写 男单 / 女单 / 男双 / 女双 / 混双，
        // 一行一个项目 —— 因此一份表可以混着放多个项目，同一人也可以出现在多行。
        boolean byColumn = header.containsKey("category");
        boolean forced = isExplicitCategory(category);
        if (!byColumn && !forced) {
            out.put("category_source", "none");
            out.put("category_error",
                    "名单里没有「参赛类别」列。请在 Excel 中新增一列「参赛类别」，"
                            + "每行填 男单 / 女单 / 男双 / 女双 / 混双 之一后重新上传。");
            out.put("players", new ArrayList<>());
            out.put("valid_count", 0);
            out.put("invalid_count", 0);
            out.put("ai_used", false);
            out.put("ai_message", "未导入：缺少「参赛类别」列");
            return out;
        }

        // 逐行解析：类别取自本行，是否双打由本行的类别决定
        List<Map<String, Object>> players = new ArrayList<>();
        int unknownCategory = 0;
        for (List<String> row : data) {
            if (isBlankRow(row)) continue;
            String cat = byColumn ? parseCategory(cell(row, header, "category"))
                    : category.trim().toUpperCase();
            Map<String, Object> p = header.isEmpty()
                    ? guessByPosition(row, isDoubles(cat))
                    : byHeader(row, header);
            boolean known = isKnownCategory(cat);
            p.put("category", known ? cat : "");
            if (!known) unknownCategory++;
            players.add(p);
        }

        // 2) 本地 Ollama 语义识别（可选增强）
        //
        // 关键设计：模型只回答「每一列是什么字段」，不逐行转写。
        //
        // 早期版本让模型把前 30 行转成 JSON，本地 7B 模型约 2-4 秒/行，
        // 一份百人名单要跑几分钟，前端一直转圈 —— 这正是导入卡死的根因。
        // 改为列映射后输出只有几十个 token，冷启动也只需数秒，
        // 且耗时与表格行数完全无关（行数再多也照样快）。
        boolean aiUsed = false;
        String aiMessage;
        if (useAi && ollama.isEnabled() && ollama.available()) {
            Map<String, Integer> map = aiColumnMap(data, header);
            if (map != null && !map.isEmpty()) {
                int filled = fillByColumnMap(players, data, map);
                aiUsed = filled > 0;
                aiMessage = aiUsed
                        ? "本地模型已识别列语义（映射 " + map.size() + " 列），补全了 " + filled + " 处字段"
                        : "本地模型识别出的列语义与规则解析一致，无需补充";
            } else {
                aiMessage = "本地模型未返回可用的列映射，已使用规则解析";
            }
        } else if (useAi) {
            aiMessage = "未检测到本地 Ollama 服务，已使用规则解析";
        } else {
            aiMessage = "未启用智能识别，使用规则解析";
        }

        // 3) 按分支分组：组队信息归一化必须在本分支范围内做
        //    （单打行清掉搭档字段，双打行把「张三/李四」拆成主选手 + 搭档）
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String c : CATEGORY_LABEL.keySet()) {
            grouped.put(c, new ArrayList<>());
        }
        for (Map<String, Object> p : players) {
            List<Map<String, Object>> bucket = grouped.get(str(p.get("category")));
            if (bucket != null) bucket.add(p);
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            if (!e.getValue().isEmpty()) resolvePairs(e.getValue(), e.getKey());
        }

        annotate(players);

        int valid = 0;
        for (Map<String, Object> p : players) {
            if (Boolean.TRUE.equals(p.get("ok"))) valid++;
        }

        // 4) 按分支汇总，供前端展示「这份表里有哪些项目、各多少」
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            List<Map<String, Object>> list = e.getValue();
            if (list.isEmpty()) continue;
            int v = 0;
            int paired = 0;
            for (Map<String, Object> p : list) {
                if (Boolean.TRUE.equals(p.get("ok"))) v++;
                if (!str(p.get("partner_name")).isEmpty()) paired++;
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("category", e.getKey());
            g.put("label", CATEGORY_LABEL.get(e.getKey()));
            g.put("doubles", isDoubles(e.getKey()));
            g.put("count", list.size());
            g.put("valid", v);
            g.put("paired", paired);
            groups.add(g);

            // 双打却一个搭档都没解析出来 → 直接说清写法，不让人猜
            if (isDoubles(e.getKey()) && paired == 0) {
                out.put("pair_hint", "「" + CATEGORY_LABEL.get(e.getKey()) + "」没解析出组队信息："
                        + "请在姓名格写成「张三/李四」，或补一列搭档姓名。");
            }
        }

        out.put("category_source", byColumn ? "column" : "param");
        out.put("groups", groups);
        out.put("players", players);
        out.put("valid_count", valid);
        out.put("invalid_count", players.size() - valid);
        out.put("ai_used", aiUsed);
        out.put("ai_model", ollama.getModel());
        out.put("ai_message", aiMessage);
        // 兼容字段：整份表只有一个项目时给出该项目
        Object only = groups.size() == 1 ? groups.get(0).get("category") : "";
        out.put("category", only);
        out.put("doubles", groups.size() == 1 && isDoubles(String.valueOf(only)));
        if (unknownCategory > 0) {
            out.put("category_warning", "有 " + unknownCategory + " 行的「参赛类别」识别不了，"
                    + "请确认写法是 男单 / 女单 / 男双 / 女双 / 混双；这些行不会被导入。");
        }
        return out;
    }

    /** 表头识别：返回 字段 → 列下标 */
    private Map<String, Integer> detectHeader(List<String> first) {
        Map<String, Integer> map = new HashMap<>();
        if (first == null) return map;
        for (int i = 0; i < first.size(); i++) {
            String cell = normalizeHeader(first.get(i));
            if (cell.isEmpty()) continue;
            String field = HEADER_ALIAS.get(cell);
            if (field != null && !map.containsKey(field)) {
                map.put(field, i);
            }
        }
        // 至少要能认出「姓名」或「学号」，否则视为无表头
        if (!map.containsKey("name") && !map.containsKey("student_id")) {
            return Collections.emptyMap();
        }
        return map;
    }

    /** 把「字段 → 列下标」还原成有序的列名列表，便于喂给大模型 */
    private List<String> headerLabels(Map<String, Integer> header) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(header.entrySet());
        list.sort(Map.Entry.comparingByValue());
        List<String> labels = new ArrayList<>();
        for (Map.Entry<String, Integer> e : list) {
            labels.add(e.getKey());
        }
        return labels;
    }

    private String normalizeHeader(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .replace(" ", "")
                .replace("　", "")
                .replace("*", "")
                .replace("（必填）", "")
                .replace("(必填)", "")
                .replace("\n", "")
                .toLowerCase();
    }

    // ========================================================================
    //  规则解析
    // ========================================================================

    private Map<String, Object> byHeader(List<String> row, Map<String, Integer> header) {
        Map<String, Object> p = blankPlayer();
        p.put("student_id", cell(row, header, "student_id"));
        p.put("name", cell(row, header, "name"));
        p.put("gender", parseGender(cell(row, header, "gender")));
        p.put("team", cell(row, header, "team"));
        p.put("phone", cell(row, header, "phone"));
        p.put("level", parseLevel(cell(row, header, "level")));
        // 双打：搭档列优先（没有搭档列时会走 resolvePairs 的「同格写法」拆解）
        p.put("partner_name", cell(row, header, "partner_name"));
        p.put("partner_student_id", cell(row, header, "partner_student_id"));
        p.put("partner_team", cell(row, header, "partner_team"));
        int seedRank = parseInt(cell(row, header, "seed_rank"), 0);
        if (seedRank > 0) {
            p.put("is_seed", true);
            p.put("seed_rank", seedRank);
        }
        // 描述性字段（单位 / 电话）允许按内容兜底；学号与姓名不补，见 fillMissing
        fillMissing(p, row);
        return p;
    }

    /** 无表头：按内容特征推断列语义（doubles=true 时把第二个姓名/学号识别为搭档） */
    private Map<String, Object> guessByPosition(List<String> row, boolean doubles) {
        Map<String, Object> p = blankPlayer();
        // 性别先清空默认值，否则「女」会被默认 M 挡住（真实踩过的坑）
        p.put("gender", "");
        List<String> leftovers = new ArrayList<>();
        int level = 0;
        boolean levelSet = false;

        for (String raw : row) {
            String c = raw == null ? "" : raw.trim();
            if (c.isEmpty()) continue;

            if (isGender(c) && isEmpty(p.get("gender"))) {
                p.put("gender", parseGender(c));
                continue;
            }
            if (PHONE.matcher(c).matches() && isEmpty(p.get("phone"))) {
                p.put("phone", c);
                continue;
            }
            if (isEmpty(p.get("student_id")) && (NUMERIC_ID.matcher(c).matches() || isAlnumId(c))) {
                p.put("student_id", c);
                continue;
            }
            if (isEmpty(p.get("name")) && CN_NAME.matcher(c).matches() && !TEAM_HINT.matcher(c).find()) {
                p.put("name", c);
                continue;
            }
            if (!levelSet && isSmallNumber(c)) {
                level = parseInt(c, 0);
                levelSet = true;
                continue;
            }
            leftovers.add(c);
        }

        // 剩余文本：按语义依次补位（双打时第二个姓名/学号属于搭档）
        for (String c : leftovers) {
            if (isEmpty(p.get("team")) && TEAM_HINT.matcher(c).find()) {
                p.put("team", c);
            } else if (isEmpty(p.get("name")) && CN_NAME.matcher(c).matches()) {
                p.put("name", c);
            } else if (doubles && isEmpty(p.get("partner_name")) && CN_NAME.matcher(c).matches()) {
                p.put("partner_name", c);
            } else if (doubles && isEmpty(p.get("partner_student_id")) && isAlnumId(c)) {
                p.put("partner_student_id", c);
            } else if (isEmpty(p.get("team")) && CN_NAME.matcher(c).matches()) {
                p.put("team", c);
            }
        }
        p.put("level", level);
        p.put("gender", parseGender(str(p.get("gender"))));
        return p;
    }

    /**
     * 表头解析后的兜底补全：只补「单位 / 电话」这类描述性字段。
     *
     * 刻意不补学号与姓名。这两个是身份字段，必须来自它们自己的列 ——
     * 早先这里会「按内容猜」，实测把「参赛类别」列的值当成了姓名补进空行里
     * （「男单」恰好是两个字的中文，能通过姓名正则），凭空造出一个叫「男单」的选手。
     * 缺姓名/学号就应该如实标成不合格，交给用户改表，而不是替用户编。
     */
    private void fillMissing(Map<String, Object> p, List<String> row) {
        for (String raw : row) {
            String c = raw == null ? "" : raw.trim();
            if (c.isEmpty()) continue;
            if (isEmpty(p.get("team")) && TEAM_HINT.matcher(c).find()) {
                p.put("team", c);
                continue;
            }
            if (isEmpty(p.get("phone")) && PHONE.matcher(c).matches()) {
                p.put("phone", c);
            }
        }
    }

    // ========================================================================
    //  本地 Ollama 语义识别
    // ========================================================================

    /**
     * 让本地模型判断「每一列是什么字段」，返回 字段 → 列下标。
     *
     * 这是解决「不规则表格识别不出来」的核心：
     * 列序打乱、表头是别名、混进无关列，都能靠一次列映射纠正。
     * 而输出只有几十个 token，所以不论表格多少行都很快。
     *
     * @return 列映射；模型不可用或输出不可用时返回 null（由规则解析兜底）
     */
    private Map<String, Integer> aiColumnMap(List<List<String>> data, Map<String, Integer> header) {
        try {
            int sample = Math.max(1, Math.min(data.size(), maxRows));
            StringBuilder sb = new StringBuilder();
            if (header.isEmpty()) {
                sb.append("该表格没有表头。\n");
            } else {
                sb.append("表头列名（按从左到右的顺序）：")
                        .append(String.join(" | ", headerLabels(header))).append('\n');
            }
            sb.append("前 ").append(sample)
                    .append(" 行数据（每行字段用 | 分隔，第 1 个字段是第 0 列）：\n");
            for (int i = 0; i < sample; i++) {
                sb.append(String.join(" | ", data.get(i))).append('\n');
            }

            String system = "你是羽毛球赛事报名名单的表格结构分析器。"
                    + "用户的表格可能没有表头、列顺序不定、含与报名无关的多余列。"
                    + "请判断「每一列」对应哪个字段，只输出 JSON，不要任何解释文字，结构为："
                    + "{\"m\":{\"s\":0,\"n\":1,\"g\":2,\"t\":3,\"lv\":4}}。"
                    + "m 的值是该字段所在的列下标，从 0 开始数（即每行里第几个字段）；"
                    + "字段含义：s=学号；n=姓名；g=性别（男/女或 M/F）；t=单位或学院；"
                    + "lv=水平分（0-100 的数字）；ph=联系电话（11 位手机号）；"
                    + "pn=搭档姓名；ps=搭档学号。"
                    + "表格里没有的字段填 -1，不要猜；同一列只能对应一个字段，不要重复。";

            String content = ollama.chatJson(system, sb.toString());
            if (content == null || content.trim().isEmpty()) return null;

            JsonNode root = mapper.readTree(content);
            JsonNode node = root.path("m");
            // 兼容模型直接输出 {"s":0,"n":1,...} 的写法
            if (!node.isObject()) node = root;
            if (!node.isObject()) return null;

            Map<String, Integer> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String field = IMPORT_FIELD.get(e.getKey().trim().toLowerCase());
                if (field == null || map.containsKey(field)) continue;
                JsonNode v = e.getValue();
                int idx = v.isNumber() ? v.asInt() : parseInt(v.asText(), -1);
                if (idx >= 0) map.put(field, idx);
            }
            // 连姓名或学号都认不出来，这份映射没有价值
            if (!map.containsKey("name") && !map.containsKey("student_id")) return null;
            return map;
        } catch (Exception e) {
            log.warn("本地模型列映射解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 把模型给出的列映射套用到全部数据行（纯本地执行，1000 行也是毫秒级） */
    private List<Map<String, Object>> applyColumnMap(List<List<String>> data,
                                                     Map<String, Integer> map) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<String> row : data) {
            if (isBlankRow(row)) continue;
            Map<String, Object> p = blankPlayer();
            p.put("gender", "");
            p.put("student_id", cell(row, map, "student_id"));
            p.put("name", cell(row, map, "name"));
            p.put("gender", parseGender(cell(row, map, "gender")));
            p.put("team", cell(row, map, "team"));
            p.put("phone", cell(row, map, "phone"));
            p.put("level", parseLevel(cell(row, map, "level")));
            p.put("partner_name", cell(row, map, "partner_name"));
            p.put("partner_student_id", cell(row, map, "partner_student_id"));
            p.put("partner_team", cell(row, map, "partner_team"));
            // 映射对个别行失效时（空单元格、行内错位），按内容特征就地补齐
            if (isEmpty(p.get("name")) || isEmpty(p.get("student_id"))) fillMissing(p, row);
            out.add(p);
        }
        return out;
    }

    /**
     * 把模型给出的列映射「补空」到规则解析结果上：只填空字段，绝不覆盖已识别到的值。
     *
     * 这么设计的原因：不必在「模型结果 vs 规则结果」之间做没有把握的取舍 ——
     * 补空是单调的，智能识别最差也只是等于没启用，永远不会让结果变差。
     *
     * 两个列表的行下标是对齐的：heuristic 与 applyColumnMap 用同一套空行跳过规则。
     *
     * @return 实际补全的字段处数
     */
    private int fillByColumnMap(List<Map<String, Object>> base, List<List<String>> data,
                                Map<String, Integer> map) {
        List<Map<String, Object>> ai = applyColumnMap(data, map);
        int filled = 0;
        int n = Math.min(base.size(), ai.size());
        for (int i = 0; i < n; i++) {
            Map<String, Object> b = base.get(i);
            Map<String, Object> a = ai.get(i);
            for (String k : new String[]{"student_id", "name", "team", "phone", "gender"}) {
                if (str(a.get(k)).isEmpty() || !str(b.get(k)).isEmpty()) continue;
                b.put(k, a.get(k));
                filled++;
            }
            // 水平分默认 0，只在规则没算出来时补
            if (parseInt(str(b.get("level")), 0) == 0) {
                int al = parseInt(str(a.get("level")), 0);
                if (al > 0) {
                    b.put("level", al);
                    filled++;
                }
            }
        }
        return filled;
    }

    /** 标注可导入性与失败原因 */
    private void annotate(List<Map<String, Object>> players) {
        for (Map<String, Object> p : players) {
            String sid = str(p.get("student_id"));
            String name = str(p.get("name"));
            String cat = str(p.get("category"));
            List<String> reasons = new ArrayList<>();
            if (cat.isEmpty()) reasons.add("「参赛类别」识别不了");
            // 单打行里出现「/」说明这行填的其实是双打组合，直接报错而不是替用户裁掉一半
            if (!cat.isEmpty() && !isDoubles(cat) && name.indexOf('/') >= 0) {
                reasons.add("单打只能填一人，姓名里不应出现「/」");
            }
            if (sid.isEmpty()) reasons.add("缺少学号");
            if (name.isEmpty()) reasons.add("缺少姓名");
            p.put("ok", reasons.isEmpty());
            p.put("reason", String.join("、", reasons));
            if (!Boolean.TRUE.equals(p.get("is_seed"))) {
                p.put("is_seed", false);
                p.put("seed_rank", 0);
            }
        }
    }

    // ========================================================================
    //  小工具
    // ========================================================================

    private Map<String, Object> blankPlayer() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("category", "MS");
        p.put("student_id", "");
        p.put("name", "");
        p.put("gender", "M");
        p.put("team", "");
        p.put("phone", "");
        p.put("level", 0);
        p.put("is_seed", false);
        p.put("seed_rank", 0);
        p.put("partner_name", "");
        p.put("partner_student_id", "");
        p.put("partner_team", "");
        return p;
    }

    /**
     * 双打组队信息归一化。
     *
     * 支持两种常见报名表写法：
     *   1. 独立的搭档列（搭档姓名 / 选手2 / 队员2 …）
     *   2. 把一对组合写在同一格里：「张三/李四」「张三、李四」
     * 最终统一为 name="主选手/搭档" + partner_name 分开存储，便于展示与追溯。
     */
    private void resolvePairs(List<Map<String, Object>> players, String category) {
        for (Map<String, Object> p : players) {
            p.put("category", category);
            if (!isDoubles(category)) {
                p.put("partner_name", "");
                p.put("partner_student_id", "");
                continue;
            }
            String mainName = str(p.get("name"));
            String partnerName = str(p.get("partner_name"));
            if (partnerName.isEmpty()) {
                String[] pair = splitPair(mainName);
                if (pair != null) {
                    mainName = pair[0];
                    partnerName = pair[1];
                }
            }
            if (!partnerName.isEmpty() && mainName.indexOf('/') < 0) {
                p.put("name", mainName.isEmpty() ? partnerName : mainName + "/" + partnerName);
            }
            p.put("partner_name", partnerName);

            String mainSid = str(p.get("student_id"));
            String partnerSid = str(p.get("partner_student_id"));
            if (partnerSid.isEmpty()) {
                String[] pair = splitPair(mainSid);
                if (pair != null) {
                    mainSid = pair[0];
                    partnerSid = pair[1];
                }
            }
            if (!mainSid.isEmpty()) p.put("student_id", mainSid);
            p.put("partner_student_id", partnerSid);
        }
    }

    /** 把「张三/李四」拆成两段；只有恰好两段且都非空才认为是一个组合 */
    private static String[] splitPair(String cell) {
        String s = cell == null ? "" : cell.trim();
        if (s.isEmpty()) return null;
        String[] parts = PAIR_SEP.split(s);
        if (parts.length < 2) return null;
        String a = parts[0].trim();
        String b = parts[1].trim();
        if (a.isEmpty() || b.isEmpty()) return null;
        return new String[]{a, b};
    }

    private String cell(List<String> row, Map<String, Integer> header, String field) {
        Integer idx = header.get(field);
        if (idx == null || idx < 0 || idx >= row.size()) return "";
        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    /** 男/女、M/F、男性/女性 → M / F */
    public static String parseGender(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return "M";
        if ("F".equalsIgnoreCase(s) || s.contains("女")) return "F";
        if ("M".equalsIgnoreCase(s) || s.contains("男")) return "M";
        return "M";
    }

    public static int parseLevel(String raw) {
        int n = parseInt(raw, 0);
        if (n < 0) n = 0;
        if (n > 100) n = 100;
        return n;
    }

    private static int parseInt(String raw, int def) {
        if (raw == null) return def;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return def;
        try {
            return Integer.parseInt(digits.length() > 6 ? digits.substring(0, 6) : digits);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean isGender(String c) {
        return "男".equals(c) || "女".equals(c) || "M".equalsIgnoreCase(c) || "F".equalsIgnoreCase(c)
                || "男性".equals(c) || "女性".equals(c);
    }

    /** 形如 A2310044118 / 2023-001 的学号 */
    private static boolean isAlnumId(String c) {
        if (c.length() < 4 || c.length() > 20) return false;
        if (!c.matches("[A-Za-z0-9\\-]+")) return false;
        return c.matches(".*\\d.*");
    }

    /** 0-100 的短数字，可能是水平分 */
    private static boolean isSmallNumber(String c) {
        if (!c.matches("\\d{1,3}")) return false;
        try {
            int n = Integer.parseInt(c);
            return n >= 0 && n <= 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isEmpty(Object v) {
        return str(v).isEmpty();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static boolean isBlankRow(List<String> row) {
        if (row == null) return true;
        for (String c : row) {
            if (c != null && !c.trim().isEmpty()) return false;
        }
        return true;
    }
}
