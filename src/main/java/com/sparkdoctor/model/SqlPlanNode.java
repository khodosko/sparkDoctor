package com.sparkdoctor.model;

import java.util.List;
import java.util.stream.Stream;

public record SqlPlanNode(
        String nodeName,
        String simpleString,
        List<SqlPlanMetric> metrics,
        List<SqlPlanNode> children) {
    public SqlPlanNode {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        children = children == null ? List.of() : List.copyOf(children);
    }

    public Stream<SqlPlanNode> traverse() {
        return Stream.concat(Stream.of(this), children.stream().flatMap(SqlPlanNode::traverse));
    }

    public int subtreeSize() {
        return 1 + children.stream()
                .mapToInt(SqlPlanNode::subtreeSize)
                .sum();
    }

    public int maxDepth() {
        return 1 + children.stream()
                .mapToInt(SqlPlanNode::maxDepth)
                .max()
                .orElse(0);
    }

    public long countByName(String name) {
        return traverse()
                .filter(node -> name.equals(node.nodeName()))
                .count();
    }
}
