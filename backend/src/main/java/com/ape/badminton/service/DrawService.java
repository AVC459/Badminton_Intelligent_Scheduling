package com.ape.badminton.service;

import com.ape.badminton.algorithm.DrawResult;
import com.ape.badminton.algorithm.FairnessReport;
import com.ape.badminton.dto.DrawConfigDto;
import com.ape.badminton.dto.DrawOutcomeVo;
import com.ape.badminton.entity.DrawLog;

import java.util.List;
import java.util.Map;

public interface DrawService {

    /** 试算抽签（不落库），供前端实时预览赛事规模与对阵预览 */
    DrawResult preview(String tournamentId, DrawConfigDto config);

    /** 正式执行抽签编排，落库并生成全部场次 */
    DrawOutcomeVo execute(String tournamentId, DrawConfigDto config);

    /** 抽签过程留痕（透明可回溯） */
    List<DrawLog> logs(String tournamentId);

    /** 基于已保存签位重算公平性报告 */
    FairnessReport fairness(String tournamentId);

    /** 小组循环赛全部完成后，生成交叉淘汰赛程 */
    Map<String, Object> generateKnockout(String tournamentId);

    /** 撤销抽签：清空赛程回到报名状态 */
    boolean reset(String tournamentId);
}
