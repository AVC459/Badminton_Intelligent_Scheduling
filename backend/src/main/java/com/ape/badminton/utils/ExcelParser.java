package com.ape.badminton.utils;

import com.ape.badminton.common.BizException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 名单解析工具
 *
 * 只负责「把表格变成二维字符串数组」，不做字段语义判断
 * —— 语义识别交给 {@link com.ape.badminton.service.OllamaClient}（本地大模型）
 * 或启发式规则兜底。
 *
 * 支持 .xlsx（XSSF）与 .xls（HSSF），读取第一个 Sheet。
 */
public final class ExcelParser {

    private ExcelParser() {
    }

    /**
     * 解析 Excel 第一个工作表为二维字符串数组（已裁剪尾部空列/空行）
     */
    public static List<List<String>> readRows(InputStream in) {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                throw new BizException(40001, "Excel 中没有工作表");
            }
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<String> cells = new ArrayList<>();
                int lastCol = row.getLastCellNum();
                for (int c = 0; c < lastCol; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                }
                trimTrailing(cells);
                if (isBlank(cells)) continue;
                rows.add(cells);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40001, "Excel 解析失败：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BizException(40001, "Excel 中没有可读取的数据行");
        }
        return rows;
    }

    /**
     * 解析粘贴的文本（CSV / 制表符 / 逗号 / 分号分隔）为二维字符串数组
     */
    public static List<List<String>> readText(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null) return rows;
        String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String[] parts = t.split("[,，\\t;；]+");
            List<String> cells = new ArrayList<>();
            for (String p : parts) cells.add(p == null ? "" : p.trim());
            trimTrailing(cells);
            if (isBlank(cells)) continue;
            rows.add(cells);
        }
        return rows;
    }

    /** 二维数组 → 便于喂给大模型的紧凑文本（每行一条，用 | 分隔字段） */
    public static String toPlainText(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            sb.append(String.join(" | ", row)).append('\n');
        }
        return sb.toString();
    }

    private static void trimTrailing(List<String> cells) {
        while (!cells.isEmpty()) {
            String last = cells.get(cells.size() - 1);
            if (last == null || last.trim().isEmpty()) {
                cells.remove(cells.size() - 1);
            } else {
                break;
            }
        }
    }

    private static boolean isBlank(List<String> cells) {
        for (String c : cells) {
            if (c != null && !c.trim().isEmpty()) return false;
        }
        return true;
    }
}
