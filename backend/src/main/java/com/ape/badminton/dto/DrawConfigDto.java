package com.ape.badminton.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽签 / 编排配置
 *
 * 所有字段默认 null，语义是「本次调用没指定，沿用赛事已有配置」。
 *
 * 这里刻意不给默认值：之前 bestOf 默认 3，于是「客户端没传」被当成「客户端传了 3」，
 * 抽签时 applyConfig 会把赛事原本的配置覆盖掉 —— 一场「1 局定胜负」的比赛
 * 执行抽签后 best_of 会被悄悄改成 3，接着记分页要求赢 2 局，
 * 1 局的比赛最多只能录 1 局，比分永远存不进去。
 */
@Data
public class DrawConfigDto {
    /** 种子数量 */
    private Integer seedCount;
    /** 是否启用同单位规避 */
    private Boolean avoidSameTeam;
    /** 小组数（小组赛制） */
    private Integer groupCount;
    /** 每组出线人数 */
    private Integer groupAdvance;
    /** 几局几胜（1=一局定胜负 / 3=三局两胜 / 5=五局三胜） */
    private Integer bestOf;
    /** 每局分数 */
    private Integer pointsPerGame;
    /** 封顶分 */
    private Integer pointsCap;
    /** 是否生成季军赛（淘汰赛）；未指定时按生成处理 */
    private Boolean thirdPlace;
    /** 随机种子，固定后可复现抽签结果 */
    private Long randomSeed;
    /** 选手名单；为空则使用参赛名单 */
    private List<String> playerIds = new ArrayList<>();
}
