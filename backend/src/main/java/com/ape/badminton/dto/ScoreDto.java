package com.ape.badminton.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 比分录入请求
 */
@Data
public class ScoreDto {
    private String matchId;
    /** 各局比分，如 [{a:21,b:18},{a:19,b:21},{a:21,b:15}] */
    private List<GameScore> games = new ArrayList<>();
    /** 弃权时直接指定胜方 */
    private Boolean walkover = false;
    /** 弃权方（可选，不填则由比分推断） */
    private String retiredPlayerId;
    private String remark;

    @Data
    public static class GameScore {
        private Integer a = 0;
        private Integer b = 0;
    }
}
