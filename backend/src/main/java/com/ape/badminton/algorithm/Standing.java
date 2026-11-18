package com.ape.badminton.algorithm;

import lombok.Data;

/**
 * 循环赛/小组赛排名行
 */
@Data
public class Standing {
    /** 名次，从 1 开始 */
    private int rank;
    private String playerId;
    private String playerName;
    private String team;
    /** 已赛场次 */
    private int played;
    private int won;
    private int lost;
    /** 胜局 / 负局 */
    private int gamesWon;
    private int gamesLost;
    /** 总得分 / 总失分 */
    private int pointsWon;
    private int pointsLost;
    /** 积分（胜一场得 2 分） */
    private int points;
    /** 是否出线 */
    private boolean advanced;

    public int getGameDiff() {
        return gamesWon - gamesLost;
    }

    public int getPointDiff() {
        return pointsWon - pointsLost;
    }
}
