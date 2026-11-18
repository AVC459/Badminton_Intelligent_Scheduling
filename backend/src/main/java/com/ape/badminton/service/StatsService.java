package com.ape.badminton.service;

import com.ape.badminton.algorithm.Standing;

import java.util.List;
import java.util.Map;

public interface StatsService {

    /** 首页数据看板 */
    Map<String, Object> overview();

    /** 整体排名（循环赛制直接可用；淘汰/小组赛返回参赛者战绩汇总） */
    List<Standing> standings(String tournamentId);

    /** 小组积分榜 */
    List<Map<String, Object>> groupStandings(String tournamentId);

    /** 赛事报告 */
    Map<String, Object> report(String tournamentId);
}
