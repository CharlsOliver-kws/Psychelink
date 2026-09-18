package com.psychic.agent.repository;

import com.psychic.agent.entity.PsychologicalReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PsychologicalReportRepository extends JpaRepository<PsychologicalReport, Long> {
    List<PsychologicalReport> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<PsychologicalReport> findByRiskLevelOrderByCreatedAtDesc(PsychologicalReport.RiskLevel riskLevel);
    List<PsychologicalReport> findByAlertSentFalseAndRiskLevelNot(PsychologicalReport.RiskLevel none);
}