package com.ape.badminton.service;

import com.ape.badminton.dto.MatchVo;
import com.ape.badminton.dto.ScoreDto;
import com.ape.badminton.entity.Match;

import java.util.List;

public interface MatchService {

    List<Match> rawByTournament(String tournamentId);

    List<MatchVo> listByTournament(String tournamentId, String stage, Integer groupNo, String status);

    MatchVo getVo(String matchId);

    Match getById(String matchId);

    MatchVo saveScore(ScoreDto dto);

    /** 批量保存场次 */
    void saveAll(List<Match> matches);

    void deleteByTournament(String tournamentId);

    void deleteByTournamentAndStage(String tournamentId, String stage);
}
