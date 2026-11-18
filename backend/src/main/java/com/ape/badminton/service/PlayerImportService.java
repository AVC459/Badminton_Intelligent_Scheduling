package com.ape.badminton.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 选手名单导入服务：Excel 解析 + 本地 Ollama 语义识别
 *
 * 选手库按项目类型分为 5 个分支（MS/WS/MD/WD/XD），导入时必须指定分支：
 * 双打分支会额外识别「组队信息」（搭档列，或写在同一个单元格里的「张三/李四」）。
 */
public interface PlayerImportService {

    /**
     * 从 Excel 文件流解析名单
     *
     * @param category 项目类型分支 MS/WS/MD/WD/XD
     * @param useAi    是否调用本地 Ollama 做字段识别
     */
    Map<String, Object> fromExcel(InputStream in, boolean useAi, String category);

    /** 从粘贴文本解析名单 */
    Map<String, Object> fromText(String text, boolean useAi, String category);

    /** 本地 Ollama 可用性（供前端提示「智能识别是否可用」） */
    Map<String, Object> aiStatus();
}
