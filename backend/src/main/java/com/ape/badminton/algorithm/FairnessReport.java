package com.ape.badminton.algorithm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽签公平性报告
 *
 * 对应项目「数据可视化看板：实时展示分组概率分布、种子覆盖度等指标，增强结果公信力」
 */
@Data
public class FairnessReport {
    /** 综合公平性得分 0~100 */
    private double score;
    /** 签位规模 */
    private int bracketSize;
    /** 各 1/4 区种子数量 */
    private List<Integer> seedPerQuarter = new ArrayList<>();
    /** 各 1/4 区实力总分 */
    private List<Integer> levelPerQuarter = new ArrayList<>();
    /** 各半区轮空数（上半区 / 下半区） */
    private List<Integer> byePerHalf = new ArrayList<>();
    /** 实力离散度（各 1/4 区实力总分的标准差） */
    private double levelStdDev;
    /** 同单位总配对数 */
    private int sameTeamPairs;
    /** 首轮即同单位相遇的配对数（死签） */
    private int firstRoundClashes;
    /** 前两轮内同单位相遇的配对数 */
    private int earlyClashes;
    /** 同单位规避达成率 0~1 */
    private double avoidanceRate;
    /** 风险提示 */
    private List<String> notes = new ArrayList<>();

    // ------------------------------------------------------------------
    //  分组赛制（GROUP_KNOCKOUT）专用指标
    // ------------------------------------------------------------------

    /** 赛制标识，便于前端区分展示哪一组指标 */
    private String format;

    /** 各组人数 */
    private List<Integer> groupSizes = new ArrayList<>();

    /** 各组种子数量：应当尽量接近，理想为「每组至少 1 名种子」 */
    private List<Integer> seedPerGroup = new ArrayList<>();

    /** 各组实力总分（level 之和）：衡量各组强弱是否均衡 */
    private List<Integer> levelPerGroup = new ArrayList<>();

    /** 各组实力总分的标准差，越小越均衡 */
    private double groupLevelStdDev;

    /** 各组实力总分极差（最大 − 最小） */
    private int groupLevelRange;

    /** 同单位选手被分入同一组的对数（死签），理想为 0 */
    private int sameTeamInGroup;

    /** 存在种子缺失的小组数量（0 最好） */
    private int groupsWithoutSeed;

    /** 因人数无法整除导致的组人数差异（最大组 − 最小组） */
    private int groupSizeSpread;

    public void addNote(String note) {
        this.notes.add(note);
    }
}
