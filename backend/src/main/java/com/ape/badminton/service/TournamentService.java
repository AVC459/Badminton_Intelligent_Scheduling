package com.ape.badminton.service;

import com.ape.badminton.entity.Tournament;

import java.util.List;

public interface TournamentService {

    /**
     * 赛事列表
     *
     * @param onlyMine true 时只返回当前登录发起者创建的赛事（「我创建的赛事」）
     */
    List<Tournament> list(String status, String keyword, boolean onlyMine);

    Tournament getById(String tournamentId);

    boolean add(Tournament tournament);

    boolean update(Tournament tournament);

    boolean delete(String tournamentId);

    long count();

    long countByStatus(String status);

    /** 指定发起者的赛事总数 */
    long countByCreator(String creatorId);

    /** 指定发起者某一状态的赛事数 */
    long countByCreatorAndStatus(String creatorId, String status);
}
