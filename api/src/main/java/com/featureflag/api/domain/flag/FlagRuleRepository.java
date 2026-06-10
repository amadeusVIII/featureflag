package com.featureflag.api.domain.flag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlagRuleRepository extends JpaRepository<FlagRule, UUID> {


    List<FlagRule> findByFlagIdOrderByRuleOrder(UUID flagId);
}
