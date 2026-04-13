package com.livelinter.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.livelinter.documents.LineIndex;
import com.livelinter.documents.TextDocumentSnapshot;
import com.livelinter.documents.TextSpan;
import com.livelinter.parser.AnalysisRegion;
import com.livelinter.parser.ParseResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnalysisService {
    public AnalysisSnapshot analyze(TextDocumentSnapshot snapshot, ParseResult parseResult) {
        long start = System.nanoTime();
        AnalysisRegion region = parseResult.analysisRegion();
        if (parseResult.compilationUnit().isEmpty()) {
            return new AnalysisSnapshot(region, List.of(), List.of(), List.of(), elapsed(start));
        }

        CompilationUnit compilationUnit = parseResult.compilationUnit().orElseThrow();
        LineIndex lineIndex = snapshot.lineIndex();
        List<VariableFact> variableFacts = collectVariableFacts(compilationUnit, lineIndex, region);
        List<UnreachableCodeFact> unreachableCodeFacts = collectUnreachableFacts(compilationUnit, lineIndex, region);
        List<LogicalPatternFact> logicalPatternFacts = collectLogicalPatterns(compilationUnit, lineIndex, region);
        return new AnalysisSnapshot(region, variableFacts, unreachableCodeFacts, logicalPatternFacts, elapsed(start));
    }

    private List<VariableFact> collectVariableFacts(CompilationUnit compilationUnit, LineIndex lineIndex, AnalysisRegion region) {
        List<VariableFact> variableFacts = new ArrayList<>();
        compilationUnit.findAll(MethodDeclaration.class).forEach(method -> method.getBody().ifPresent(body -> {
            Set<String> usages = collectNameUsages(body);
            for (Parameter parameter : method.getParameters()) {
                TextSpan span = AstSupport.toSpan(lineIndex, parameter, region.span());
                if (span != null) {
                    variableFacts.add(new VariableFact(parameter.getNameAsString(), span, true, usages.contains(parameter.getNameAsString())));
                }
            }
        }));
        compilationUnit.findAll(ConstructorDeclaration.class).forEach(constructor -> {
            Set<String> usages = collectNameUsages(constructor.getBody());
            for (Parameter parameter : constructor.getParameters()) {
                TextSpan span = AstSupport.toSpan(lineIndex, parameter, region.span());
                if (span != null) {
                    variableFacts.add(new VariableFact(parameter.getNameAsString(), span, true, usages.contains(parameter.getNameAsString())));
                }
            }
        });
        compilationUnit.findAll(BlockStmt.class).forEach(block -> variableFacts.addAll(analyzeBlockOnly(block, lineIndex, region)));
        return variableFacts;
    }

    private List<VariableFact> analyzeBlockOnly(BlockStmt block, LineIndex lineIndex, AnalysisRegion region) {
        Set<String> usages = collectNameUsages(block);
        List<VariableFact> facts = new ArrayList<>();
        for (VariableDeclarator declarator : block.findAll(VariableDeclarator.class)) {
            if (declarator.findAncestor(BlockStmt.class).filter(block::equals).isEmpty()) {
                continue;
            }
            TextSpan span = AstSupport.toSpan(lineIndex, declarator, region.span());
            if (span == null) {
                continue;
            }
            facts.add(new VariableFact(declarator.getNameAsString(), span, false, usages.contains(declarator.getNameAsString())));
        }
        return facts;
    }

    private Set<String> collectNameUsages(Node root) {
        Set<String> usages = new HashSet<>();
        root.findAll(NameExpr.class).forEach(nameExpr -> usages.add(nameExpr.getNameAsString()));
        return usages;
    }

    private List<UnreachableCodeFact> collectUnreachableFacts(CompilationUnit compilationUnit, LineIndex lineIndex, AnalysisRegion region) {
        List<UnreachableCodeFact> facts = new ArrayList<>();
        compilationUnit.findAll(BlockStmt.class).forEach(block -> inspectBlock(block, lineIndex, region, facts));
        return facts;
    }

    private void inspectBlock(BlockStmt block, LineIndex lineIndex, AnalysisRegion region, List<UnreachableCodeFact> facts) {
        boolean terminalSeen = false;
        String reason = "This statement cannot be reached because the previous statement always exits the current flow.";
        for (Statement statement : block.getStatements()) {
            TextSpan span = AstSupport.toSpan(lineIndex, statement, region.span());
            if (terminalSeen && span != null) {
                facts.add(new UnreachableCodeFact(span, reason));
            }
            if (!terminalSeen) {
                terminalSeen = statementTerminates(statement);
            }
        }
    }

    private boolean statementTerminates(Statement statement) {
        if (statement instanceof ReturnStmt || statement instanceof ThrowStmt || statement instanceof BreakStmt || statement instanceof ContinueStmt) {
            return true;
        }
        if (statement.isBlockStmt()) {
            BlockStmt blockStmt = statement.asBlockStmt();
            if (blockStmt.getStatements().isEmpty()) {
                return false;
            }
            return statementTerminates(blockStmt.getStatements().get(blockStmt.getStatements().size() - 1));
        }
        if (statement.isIfStmt()) {
            IfStmt ifStmt = statement.asIfStmt();
            return ifStmt.getElseStmt().isPresent()
                    && statementTerminates(ifStmt.getThenStmt())
                    && statementTerminates(ifStmt.getElseStmt().orElseThrow());
        }
        return false;
    }

    private List<LogicalPatternFact> collectLogicalPatterns(CompilationUnit compilationUnit, LineIndex lineIndex, AnalysisRegion region) {
        List<LogicalPatternFact> facts = new ArrayList<>();
        compilationUnit.accept(new VoidVisitorAdapter<List<LogicalPatternFact>>() {
            @Override
            public void visit(IfStmt n, List<LogicalPatternFact> collector) {
                super.visit(n, collector);
                collectConstantCondition(lineIndex, region, collector, n.getCondition(), "logical-constant-if", "This if condition is constant.");
                collectEmptyBlock(lineIndex, region, collector, n.getThenStmt(), "logical-empty-then", "This if branch has an empty block.");
                n.getElseStmt().ifPresent(stmt -> collectEmptyBlock(lineIndex, region, collector, stmt, "logical-empty-else", "This else branch has an empty block."));
            }

            @Override
            public void visit(WhileStmt n, List<LogicalPatternFact> collector) {
                super.visit(n, collector);
                collectConstantCondition(lineIndex, region, collector, n.getCondition(), "logical-constant-while", "This while condition is constant.");
                collectEmptyBlock(lineIndex, region, collector, n.getBody(), "logical-empty-while", "This while loop body is empty.");
            }

            @Override
            public void visit(DoStmt n, List<LogicalPatternFact> collector) {
                super.visit(n, collector);
                collectConstantCondition(lineIndex, region, collector, n.getCondition(), "logical-constant-do", "This do/while condition is constant.");
                collectEmptyBlock(lineIndex, region, collector, n.getBody(), "logical-empty-do", "This do/while body is empty.");
            }

            @Override
            public void visit(ForStmt n, List<LogicalPatternFact> collector) {
                super.visit(n, collector);
                n.getCompare().ifPresent(compare -> collectConstantCondition(lineIndex, region, collector, compare, "logical-constant-for", "This for-loop condition is constant."));
                collectEmptyBlock(lineIndex, region, collector, n.getBody(), "logical-empty-for", "This for-loop body is empty.");
            }

            @Override
            public void visit(TryStmt n, List<LogicalPatternFact> collector) {
                super.visit(n, collector);
                collectEmptyBlock(lineIndex, region, collector, n.getTryBlock(), "logical-empty-try", "This try block is empty.");
                n.getCatchClauses().forEach(catchClause -> collectEmptyBlock(lineIndex, region, collector, catchClause.getBody(), "logical-empty-catch", "This catch block is empty."));
                n.getFinallyBlock().ifPresent(block -> collectEmptyBlock(lineIndex, region, collector, block, "logical-empty-finally", "This finally block is empty."));
            }
        }, facts);
        return facts;
    }

    private void collectConstantCondition(LineIndex lineIndex, AnalysisRegion region, List<LogicalPatternFact> collector, Node condition, String code, String message) {
        if (condition instanceof BooleanLiteralExpr) {
            TextSpan span = AstSupport.toSpan(lineIndex, condition, region.span());
            if (span != null) {
                collector.add(new LogicalPatternFact(code, span, message, "Consider removing the redundant condition or the dead branch."));
            }
        }
    }

    private void collectEmptyBlock(LineIndex lineIndex, AnalysisRegion region, List<LogicalPatternFact> collector, Node node, String code, String message) {
        if (node instanceof BlockStmt blockStmt && blockStmt.getStatements().isEmpty()) {
            TextSpan span = AstSupport.toSpan(lineIndex, blockStmt, region.span());
            if (span != null) {
                collector.add(new LogicalPatternFact(code, span, message, "Consider removing the block or adding the intended implementation."));
            }
        }
    }

    private long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
