package com.ape.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 参赛名单条目（替代原「报名记录」）
 *
 * 系统无报名流程：比赛发起者从自己的选手库中直接选拔选手加入赛事，
 * 本表即抽签池的唯一数据来源。seed_flag / seed_rank 为「赛事级」种子信息，
 * 与选手库中的默认种子互不影响。
 */
@Data
@TableName("biz_local_entries")
public class Entry {
    @TableId
    private String entry_id;
    private String tournament_id;
    private String player_id;
    /** 本赛事中是否作为种子 */
    private Boolean seed_flag;
    /** 本赛事种子顺位 */
    private Integer seed_rank;
    private Date create_time;
}
