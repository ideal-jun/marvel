package com.marvel.module.system.excel;

import com.marvel.common.exception.BusinessException;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.service.SysUserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UserExcelServiceTest {

    private final SysUserService userService = mock(SysUserService.class);
    private final UserExcelService service = new UserExcelService(userService);

    private SysUser user(String username, String nickname, Long deptId, String status) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setNickname(nickname);
        u.setDeptId(deptId);
        u.setStatus(status);
        u.setEmail(username + "@x.com");
        u.setPhone("13800000000");
        return u;
    }

    private byte[] workbookOf(String[]... dataRows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用户");
            Row header = sheet.createRow(0);
            String[] headers = {"用户名", "昵称", "部门ID", "邮箱", "手机号", "状态", "初始密码"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (String[] data : dataRows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < data.length; i++) {
                    row.createCell(i).setCellValue(data[i]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void exportWritesHeaderAndRowsWithoutPasswords() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(List.of(user("alice", "爱丽丝", 103L, "0")), out);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("用户");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("用户名");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("初始密码");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("alice");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("103");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void importCreatesUsersAndCounts() throws Exception {
        byte[] bytes = workbookOf(
                new String[]{"alice", "爱丽丝", "103", "a@x.com", "138", "0", "Strong123"},
                new String[]{"bob", "鲍勃", "104", "b@x.com", "139", "0", "Strong123"});

        Map<String, Object> result = service.importUsers(new ByteArrayInputStream(bytes));

        assertThat(result.get("total")).isEqualTo(2);
        assertThat(result.get("success")).isEqualTo(2);
        assertThat(result.get("failed")).isEqualTo(0);
        verify(userService, times(2)).createUser(any(SysUser.class), isNull());
    }

    @Test
    void importCollectsRowErrorsWithoutAborting() throws Exception {
        doThrow(new BusinessException("密码长度需为 8-32 位"))
                .when(userService).createUser(any(SysUser.class), isNull());
        byte[] bytes = workbookOf(new String[]{"bad", "x", "", "", "", "0", "123"});

        Map<String, Object> result = service.importUsers(new ByteArrayInputStream(bytes));

        assertThat(result.get("total")).isEqualTo(1);
        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).get("row")).isEqualTo(2);
        assertThat(errors.get(0).get("message").toString()).contains("密码");
    }

    @Test
    void importRejectsNonNumericDeptId() throws Exception {
        byte[] bytes = workbookOf(new String[]{"x", "x", "abc", "", "", "0", "Strong123"});

        Map<String, Object> result = service.importUsers(new ByteArrayInputStream(bytes));

        assertThat(result.get("failed")).isEqualTo(1);
        verify(userService, never()).createUser(any(SysUser.class), any());
    }
}
