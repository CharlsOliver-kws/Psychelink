package io.github.charlsoliver.psychelink.service;

import io.github.charlsoliver.psychelink.entity.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 意图/风险分类逻辑单测（LLM 输出解析 + 规则兜底）
 */
class PsychologicalServiceTest {

    // ---------- 意图解析 ----------

    @Test
    void parseIntentShouldRecognizeRisk() {
        assertThat(PsychologicalService.parseIntent("RISK")).isEqualTo(ChatMessage.MessageIntent.RISK);
        assertThat(PsychologicalService.parseIntent(" risk ")).isEqualTo(ChatMessage.MessageIntent.RISK);
        assertThat(PsychologicalService.parseIntent("该消息属于RISK类别")).isEqualTo(ChatMessage.MessageIntent.RISK);
    }

    @Test
    void parseIntentShouldRecognizeConsult() {
        assertThat(PsychologicalService.parseIntent("CONSULT")).isEqualTo(ChatMessage.MessageIntent.CONSULT);
        assertThat(PsychologicalService.parseIntent("Consult")).isEqualTo(ChatMessage.MessageIntent.CONSULT);
    }

    @Test
    void parseIntentShouldFallbackToChat() {
        assertThat(PsychologicalService.parseIntent("CHAT")).isEqualTo(ChatMessage.MessageIntent.CHAT);
        assertThat(PsychologicalService.parseIntent("无关内容")).isEqualTo(ChatMessage.MessageIntent.CHAT);
        assertThat(PsychologicalService.parseIntent(null)).isEqualTo(ChatMessage.MessageIntent.CHAT);
    }

    // ---------- 风险解析 ----------

    @Test
    void parseRiskShouldRecognizeAllLevels() {
        assertThat(PsychologicalService.parseRisk("HIGH")).isEqualTo(ChatMessage.RiskLevel.HIGH);
        assertThat(PsychologicalService.parseRisk("MEDIUM")).isEqualTo(ChatMessage.RiskLevel.MEDIUM);
        assertThat(PsychologicalService.parseRisk("LOW")).isEqualTo(ChatMessage.RiskLevel.LOW);
        assertThat(PsychologicalService.parseRisk("NONE")).isEqualTo(ChatMessage.RiskLevel.NONE);
        assertThat(PsychologicalService.parseRisk(null)).isEqualTo(ChatMessage.RiskLevel.NONE);
    }

    // ---------- 规则兜底（LLM 不可用时保证 RISK 不漏判）----------

    @Test
    void fallbackIntentShouldDetectHighRiskKeywords() {
        assertThat(PsychologicalService.fallbackIntent("我不想活了"))
                .isEqualTo(ChatMessage.MessageIntent.RISK);
        assertThat(PsychologicalService.fallbackIntent("有自杀的念头"))
                .isEqualTo(ChatMessage.MessageIntent.RISK);
    }

    @Test
    void fallbackIntentShouldDetectConsultKeywords() {
        assertThat(PsychologicalService.fallbackIntent("最近失眠很严重"))
                .isEqualTo(ChatMessage.MessageIntent.CONSULT);
        assertThat(PsychologicalService.fallbackIntent("考试压力好大"))
                .isEqualTo(ChatMessage.MessageIntent.CONSULT);
    }

    @Test
    void fallbackIntentShouldDefaultToChat() {
        assertThat(PsychologicalService.fallbackIntent("今天天气怎么样"))
                .isEqualTo(ChatMessage.MessageIntent.CHAT);
        assertThat(PsychologicalService.fallbackIntent(null))
                .isEqualTo(ChatMessage.MessageIntent.CHAT);
    }

    // ---------- 高风险信号 ----------

    @Test
    void hasHighRiskSignalShouldMatchKeywords() {
        assertThat(PsychologicalService.hasHighRiskSignalStatic("我想割腕")).isTrue();
        assertThat(PsychologicalService.hasHighRiskSignalStatic("活着没意义")).isTrue();
        assertThat(PsychologicalService.hasHighRiskSignalStatic("今天心情不错")).isFalse();
        assertThat(PsychologicalService.hasHighRiskSignalStatic(null)).isFalse();
        assertThat(PsychologicalService.hasHighRiskSignalStatic("   ")).isFalse();
    }
}
