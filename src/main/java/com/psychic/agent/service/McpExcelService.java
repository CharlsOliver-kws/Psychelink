package com.psychic.agent.service;

import com.psychic.agent.entity.ChatMessage;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 风险数据 Excel 台账工具 —— 同时通过 MCP Server 暴露给外部 AI 客户端
 *
 * 面向真实使用者的选择：学校的心理老师、辅导员用 Excel，不用 BI 平台。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpExcelService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.excel.path:psychological_data.xlsx}")
    private String excelPath;

    @Value("${app.excel.enabled:true}")
    private boolean excelEnabled;

    /**
     * 记录风险数据（线程安全：同步方法）
     */
    @WithSpan("mcp_excel")
    public synchronized void recordRiskData(String username, ChatMessage.RiskLevel riskLevel, String message) {
        if (!excelEnabled) {
            return;
        }
        try {
            writeToExcel(username, riskLevel, message);
            log.info("风险数据已记录到 Excel: user={}, risk={}", username, riskLevel);
        } catch (Exception e) {
            log.error("写入 Excel 失败", e);
        }
    }

    /**
     * MCP 工具：记录一条心理风险数据到 Excel 台账（供外部 MCP 客户端调用）
     */
    @Tool(description = "将一条心理风险记录写入 Excel 台账文件，包含时间、用户、风险等级与消息内容")
    public String recordRiskDataTool(
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "风险等级: LOW / MEDIUM / HIGH") String riskLevel,
            @ToolParam(description = "触发记录的消息内容") String message) {
        ChatMessage.RiskLevel level;
        try {
            level = ChatMessage.RiskLevel.valueOf(riskLevel.trim().toUpperCase());
        } catch (Exception e) {
            return "无效的风险等级: " + riskLevel + "（应为 LOW / MEDIUM / HIGH）";
        }
        recordRiskData(username, level, message);
        return "已记录: " + username + " / " + level;
    }

    private void writeToExcel(String username, ChatMessage.RiskLevel riskLevel, String message) throws IOException {
        File excelFile = new File(excelPath);
        Workbook workbook;

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

        try {
            Sheet sheet = workbook.getSheet("心理数据");
            if (sheet == null) {
                sheet = workbook.createSheet("心理数据");
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("时间");
                headerRow.createCell(1).setCellValue("用户名");
                headerRow.createCell(2).setCellValue("风险等级");
                headerRow.createCell(3).setCellValue("消息内容");
            }

            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            row.createCell(0).setCellValue(LocalDateTime.now().format(FORMATTER));
            row.createCell(1).setCellValue(username != null ? username : "Anonymous");
            row.createCell(2).setCellValue(riskLevel.name());
            row.createCell(3).setCellValue(message);

            try (FileOutputStream outputStream = new FileOutputStream(excelFile)) {
                workbook.write(outputStream);
            }
        } finally {
            workbook.close();
        }
    }
}
