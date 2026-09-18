package com.psychic.agent.repository;

import com.psychic.agent.entity.RiskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskEventRepository extends JpaRepository<RiskEvent, Long> {

    List<RiskEvent> findTop200ByOrderByCreatedAtDesc();

    List<RiskEvent> findTop200ByStatusOrderByCreatedAtDesc(RiskEvent.ReviewStatus status);
}
