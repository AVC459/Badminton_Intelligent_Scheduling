package com.ape.badminton.controller;

import com.ape.badminton.common.ApiResponse;
import com.ape.badminton.common.BizException;
import com.ape.badminton.common.QueryParams;
import com.ape.badminton.entity.Player;
import com.ape.badminton.service.PlayerImportService;
import com.ape.badminton.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 选手库接口（按比赛发起者隔离）
 *
 * 导入链路：
 *   POST /api/player/import/excel  上传 Excel → 解析 + 本地 Ollama 识别 → 返回预览（不落库）
 *   POST /api/player/import/text   粘贴文本 → 解析 + 本地 Ollama 识别 → 返回预览（不落库）
 *   POST /api/player/batchImport   预览确认后落库
 */
@RestController
@RequestMapping("/api/player")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerImportService playerImportService;

    /**
     * 我的选手库，支持关键字 / 单位 / 仅种子 过滤
     *
     * seedOnly 用 String 接收而非 Boolean：小程序可能把 undefined 序列化进查询串，
     * 直接绑 Boolean 会因转换失败返回 400，这里清洗后再解析。
     */
    @GetMapping("/getAll")
    public ApiResponse<List<Player>> getAll(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String team,
                                            @RequestParam(required = false) String seedOnly,
                                            @RequestParam(required = false) String category) {
        return ApiResponse.ok(playerService.list(
                QueryParams.text(keyword), QueryParams.text(team), QueryParams.bool(seedOnly),
                QueryParams.text(category)));
    }

    @GetMapping("/getById/{id}")
    public ApiResponse<Player> getById(@PathVariable String id) {
        Player p = playerService.getById(id);
        // 选手库按账号隔离：读取他人选手时与「不存在」同样处理
        if (p == null) throw new BizException(40400, "选手不存在");
        return ApiResponse.ok(p);
    }

    @PostMapping("/add")
    public ApiResponse<Boolean> add(@RequestBody Player player) {
        if (player.getStudent_id() == null || player.getStudent_id().trim().isEmpty()
                || player.getName() == null || player.getName().trim().isEmpty()) {
            throw new BizException(40001, "学号与姓名不能为空");
        }
        return ApiResponse.ok(playerService.add(player));
    }

    @PutMapping("/update")
    public ApiResponse<Boolean> update(@RequestBody Player player) {
        if (player.getPlayer_id() == null || player.getPlayer_id().isEmpty()) {
            throw new BizException(40001, "缺少选手ID");
        }
        return ApiResponse.ok(playerService.update(player));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Boolean> delete(@PathVariable String id) {
        return ApiResponse.ok(playerService.delete(id));
    }

    /** 手动添加 / 解析预览确认后的批量落库 */
    @PostMapping("/batchImport")
    public ApiResponse<Integer> batchImport(@RequestBody List<Player> players) {
        if (players == null || players.isEmpty()) {
            throw new BizException(40001, "导入列表为空");
        }
        return ApiResponse.ok(playerService.batchImport(players));
    }

    /** 我的选手库中出现的全部单位（用于筛选器与统计） */
    @GetMapping("/teams")
    public ApiResponse<List<String>> teams() {
        return ApiResponse.ok(playerService.listTeams());
    }

    // ==================== Excel / 智能识别导入 ====================

    /** 本地 Ollama 智能识别是否就绪 */
    @GetMapping("/import/aiStatus")
    public ApiResponse<Map<String, Object>> aiStatus() {
        return ApiResponse.ok(playerImportService.aiStatus());
    }

    /**
     * 上传 Excel 名单并解析（不落库，仅返回预览）
     *
     * @param useAi 是否调用本地 Ollama 做字段语义识别
     */
    @PostMapping("/import/excel")
    public ApiResponse<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false, defaultValue = "true") Boolean useAi,
                                                        @RequestParam(required = false) String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException(40001, "请选择要导入的 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BizException(40001, "仅支持 .xlsx / .xls 格式，粘贴文本请使用「文本导入」");
        }
        try {
            return ApiResponse.ok(playerImportService.fromExcel(file.getInputStream(),
                    !Boolean.FALSE.equals(useAi), categoryOf(QueryParams.text(category))));
        } catch (IOException e) {
            throw new BizException(40001, "读取文件失败：" + e.getMessage());
        }
    }

    /** 粘贴文本名单并解析（不落库，仅返回预览） */
    @PostMapping("/import/text")
    public ApiResponse<Map<String, Object>> importText(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("text") == null) {
            throw new BizException(40001, "缺少待解析文本");
        }
        boolean useAi = !Boolean.FALSE.equals(body.get("use_ai"));
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        return ApiResponse.ok(playerImportService.fromText(
                String.valueOf(body.get("text")), useAi, categoryOf(QueryParams.text(category))));
    }

    /**
     * 导入分支：未指定时返回 AUTO，由导入服务按名单内容自动判定。
     *
     * 不再默认「男单」——默认值等于替用户做了一个系统本可以自己判断的选择，
     * 一旦用户没注意（例如导入男双名单时停在默认的男单），名单就会落错分支。
     */
    private static String categoryOf(String category) {
        return category == null || category.trim().isEmpty() ? "AUTO" : category.toUpperCase();
    }
}
