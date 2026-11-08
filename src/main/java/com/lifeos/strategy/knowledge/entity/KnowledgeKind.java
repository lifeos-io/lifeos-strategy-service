package com.lifeos.strategy.knowledge.entity;

public enum KnowledgeKind {
    /** Believed true, e.g. "there are remote in-house roles open to Eskişehir". */
    FACT,
    /** Taken as true without proof yet, e.g. "target companies want K8s experience". */
    ASSUMPTION,
    /** A known gap, e.g. "is system design asked in the first technical round?". */
    UNKNOWN
}
