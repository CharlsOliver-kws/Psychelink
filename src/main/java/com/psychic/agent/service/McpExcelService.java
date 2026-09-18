package com.psychic.agent.service;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import com.psychic.agent.config.McpConfig;
import com.psychic.agent.entity.ChatMessage;
import com.psychic.agent.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MCP Excel 写入服务 - 负责将对话和风险数据持久化到 Excel
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpExcelService {

    private final McpConfig mcpConfig;

    private static final String EXCEL_PATH = "psychological_data.xlsx";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 记录心理状态数据 (线程安全简化版：同步方法)
     */
    @WithSpan("mcp_excel")
    public synchronized void recordPsychologicalData(User user, ChatMessage.RiskLevel riskLevel, String message) {
        if (!mcpConfig.isExcelEnabled()) {
            log.info("Excel 记录功能已禁用");
            return;
        }

        try {
            writeToExcel(user, riskLevel, message);
            log.info("心理状态数据已记录到 Excel: user={}, risk={}", user.getUsername(), riskLevel);
        } catch (Exception e) {
            log.error("写入 Excel 失败", e);
        }
    }

    private void writeToExcel(User user, ChatMessage.RiskLevel riskLevel, String message) throws IOException {
        File excelFile = new File(EXCEL_PATH);
        Workbook workbook;
        Sheet sheet;

        // 1. 加载现有文件或创建新文件
        if (excelFile.exists() && excelFile.length() > 0) {
            try (FileInputStream fis = new FileInputStream(excelFile)) {
                workbook = new XSSFWorkbook(fis);
            } catch (IOException e) {
                log.warn("无法读取现有 Excel 文件，将创建新文件: {}", e.getMessage());
                workbook = new XSSFWorkbook();
            }
        } else {
            workbook = new XSSFWorkbook();
        }

        // 2. 获取或创建工作表
        sheet = workbook.getSheet("心理数据");
        if (sheet == null) {
            sheet = workbook.createSheet("心理数据");
            // 创建表头
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("时间");
            headerRow.createCell(1).setCellValue("用户名");
            headerRow.createCell(2).setCellValue("风险等级");
            headerRow.createCell(3).setCellValue("消息内容");
        }

        // 3. 追加数据行
        int lastRowNum = sheet.getLastRowNum();
        Row row = sheet.createRow(lastRowNum + 1);
        row.createCell(0).setCellValue(LocalDateTime.now().format(FORMATTER));
        row.createCell(1).setCellValue(user.getUsername() != null ? user.getUsername() : "Anonymous");
        row.createCell(2).setCellValue(riskLevel.name());
        row.createCell(3).setCellValue(message);

        // 自动调整列宽 (可选，性能开销大，暂不开启)
        // for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        // 4. 写入文件
        try (FileOutputStream outputStream = new FileOutputStream(excelFile)) {
            workbook.write(outputStream);
        } finally {
            workbook.close();
        }
    }
}
