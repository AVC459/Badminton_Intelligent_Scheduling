package com.ape.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 抽签审计日志
 * 抽签过程逐步留痕，是「公开透明」这一项目创新点的落地支撑
 */
@Data
@TableName("biz_local_draw_logs")
public class DrawLog {
    @TableId
    private String log_id;
    private String tournament_id;
    private Integer seq;
    private String step;
    private String message;
    private String snapshot;
    private Date create_time;
}
