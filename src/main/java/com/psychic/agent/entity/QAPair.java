package com.psychic.agent.entity;

/**
 * 简单的问答对实体，用于知识库存储
 */
public class QAPair {
    private String question;
    private String answer;
    private String category; // e.g., 抑郁, 焦虑, 自杀风险等
    private String riskLevel; // LOW, MEDIUM, HIGH, NONE
    private String source; // 知识来源

    public QAPair() {}

    public QAPair(String question, String answer, String category, String riskLevel, String source) {
        this.question = question;
        this.answer = answer;
        this.category = category;
        this.riskLevel = riskLevel;
        this.source = source;
    }

    // Getters and Setters
    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}