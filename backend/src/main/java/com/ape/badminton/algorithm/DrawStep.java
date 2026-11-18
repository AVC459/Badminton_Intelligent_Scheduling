package com.ape.badminton.algorithm;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 抽签过程的一步留痕（用于「公开透明」的抽签可回溯展示）
 */
@Data
@AllArgsConstructor
public class DrawStep {
    /** 步骤序号 */
    private int seq;
    /** SEED 种子落位 / BYE 轮空分配 / TEAM 同单位规避 / FILL 填充 / REPAIR 死签修正 / VERIFY 校验 */
    private String step;
    /** 人类可读说明 */
    private String message;
    /** 该步骤后的签位快照，用 | 分隔 */
    private String snapshot;
}
