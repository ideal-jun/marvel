package com.marvel.module.system.excel;

import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户 Excel 导入导出（Apache POI）。
 *
 * <p>导出不包含密码列内容（避免密文外泄）；导入按行列校验，逐行失败不影响其余行，
 * 最终返回成功/失败统计与失败明细。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserExcelService {

    private static final String SHEET_NAME = "用户";
    private static final String[] HEADERS =
            {"用户名", "昵称", "部门ID", "邮箱", "手机号", "状态(0正常1停用)", "初始密码"};
    /** 单次导入最大数据行，避免超大文件拖垮服务 */
    private static final int MAX_IMPORT_ROWS = 2000;

    private final SysUserService userService;

    /** 导出用户到 xlsx */
    public void export(List<SysUser> users, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            for (SysUser user : users) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(text(user.getUsername()));
                row.createCell(1).setCellValue(text(user.getNickname()));
                row.createCell(2).setCellValue(user.getDeptId() == null ? "" : String.valueOf(user.getDeptId()));
                row.createCell(3).setCellValue(text(user.getEmail()));
                row.createCell(4).setCellValue(text(user.getPhone()));
                row.createCell(5).setCellValue(text(user.getStatus()));
                // 密码列留空：导出绝不携带密码密文
                row.createCell(6).setCellValue("");
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            workbook.write(out);
        }
    }

    /** 解析导入文件并逐行创建用户，返回 {total, success, failed, errors} */
    public Map<String, Object> importUsers(InputStream in) throws IOException {
        int total = 0;
        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String username = cell(formatter, row, 0);
                String nickname = cell(formatter, row, 1);
                if (!StringUtils.hasText(username) && !StringUtils.hasText(nickname)) {
                    continue; // 跳过空行
                }
                if (total >= MAX_IMPORT_ROWS) {
                    errors.add(rowError(r + 1, username, "超过单次导入上限 " + MAX_IMPORT_ROWS + " 行"));
                    break;
                }
                total++;

                SysUser user = new SysUser();
                user.setUsername(username);
                user.setNickname(nickname);
                String deptId = cell(formatter, row, 2);
                if (StringUtils.hasText(deptId)) {
                    try {
                        user.setDeptId(Long.parseLong(deptId.trim()));
                    } catch (NumberFormatException e) {
                        errors.add(rowError(r + 1, username, "部门ID必须是数字"));
                        continue;
                    }
                }
                user.setEmail(cell(formatter, row, 3));
                user.setPhone(cell(formatter, row, 4));
                String status = cell(formatter, row, 5);
                user.setStatus(StringUtils.hasText(status) ? status : "0");
                user.setPassword(cell(formatter, row, 6));

                try {
                    userService.createUser(user, null);
                    success++;
                } catch (Exception e) {
                    errors.add(rowError(r + 1, username, e.getMessage()));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }

    private String cell(DataFormatter formatter, Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private Map<String, Object> rowError(int row, String username, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("row", row);
        error.put("username", username);
        error.put("message", message == null ? "导入失败" : message);
        return error;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
