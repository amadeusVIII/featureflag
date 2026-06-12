package com.featureflag.api.domain.flag;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "flag_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flag_id", nullable = false)
    private Flag flag;

    @Column(name = "rule_order", nullable = false)
    @Builder.Default
    private int ruleOrder = 0;

    @Column(nullable = false, length = 100)
    private String attribute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleOperator operator;


    @Column(name = "\"values\"", nullable = false, columnDefinition = "TEXT")
    private String values;

    @Column(name = "serve_value", nullable = false)
    @Builder.Default
    private boolean serveValue = true;
}
