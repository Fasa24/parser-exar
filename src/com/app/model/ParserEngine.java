package com.app.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ParserEngine {

    private enum TokenKind {
        IDENTIFIER, CONSTANT, PLUS, MINUS, MUL, DIV,
        LPAREN, RPAREN, SEMICOLON, UNKNOWN_SYMBOL, INVALID_ELEMENT
    }

    private enum StackSymbol {
        BOTTOM, EXPR, EXPR_TAIL, TERM, TERM_TAIL, FACTOR,
        LPAREN, RPAREN, PLUS, MINUS, MUL, DIV, OPERAND
    }

    private record LexToken(TokenKind kind, String lexeme) {}

    private record ParseIssue(String code, String message) {}

    public List<ParseResult> parseInput(String text) {
        List<ParseResult> results = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return results;
        }

        String[] lines = text.split("\\R", -1);
        int resultId = 1;

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            int lineNumber = lineIdx + 1;
            String line = lines[lineIdx];
            if (line.trim().isEmpty()) {
                continue;
            }

            List<LexToken> allTokens = tokenizeLine(line);
            List<List<LexToken>> segments = splitBySemicolon(allTokens);
            boolean endsWithSemicolon = !allTokens.isEmpty()
                    && allTokens.getLast().kind() == TokenKind.SEMICOLON;

            for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
                List<LexToken> segment = segments.get(segIdx);
                String exprText = formatTokens(segment);
                String lineTag = "Line " + formatLine(lineNumber) + ": ";

                if (segment.isEmpty()) {
                    results.add(new ParseResult(
                            resultId++, lineNumber, ";", "203", lineTag + "Missing identifier/constant."
                    ));
                    continue;
                }

                List<ParseIssue> issues = new ArrayList<>();
                List<LexToken> pdaTokens = new ArrayList<>();
                boolean hasOnlyUnknownSymbols = true;

                for (LexToken tok : segment) {
                    if (tok.kind() == TokenKind.UNKNOWN_SYMBOL) {
                        issues.add(new ParseIssue("101", lineTag + "Unknown symbol '" + tok.lexeme() + "'."));
                        continue;
                    }
                    hasOnlyUnknownSymbols = false;
                    if (tok.kind() == TokenKind.INVALID_ELEMENT) {
                        issues.add(new ParseIssue("102", lineTag + "Invalid element '" + tok.lexeme() + "'."));
                        pdaTokens.add(new LexToken(TokenKind.IDENTIFIER, tok.lexeme()));
                    } else {
                        pdaTokens.add(tok);
                    }
                }

                if (!pdaTokens.isEmpty()) {
                    issues.addAll(runPushdownAutomaton(pdaTokens, lineNumber));
                }

                if (segIdx == segments.size() - 1 && !endsWithSemicolon && !hasOnlyUnknownSymbols) {
                    issues.add(new ParseIssue("201", lineTag + "Missing delimiter ';'."));
                }

                if (issues.isEmpty()) {
                    results.add(new ParseResult(resultId++, lineNumber, exprText, "100", lineTag + "No error."));
                } else {
                    for (ParseIssue issue : issues) {
                        results.add(new ParseResult(resultId++, lineNumber, exprText, issue.code(), issue.message()));
                    }
                }
            }
        }
        return results;
    }

    private String formatLine(int lineNumber) {
        return lineNumber < 10 ? "0" + lineNumber : String.valueOf(lineNumber);
    }

    private LexToken singleCharToken(char c) {
        return switch (c) {
            case ';' -> new LexToken(TokenKind.SEMICOLON, ";");
            case '(' -> new LexToken(TokenKind.LPAREN, "(");
            case ')' -> new LexToken(TokenKind.RPAREN, ")");
            case '+' -> new LexToken(TokenKind.PLUS, "+");
            case '-' -> new LexToken(TokenKind.MINUS, "-");
            case '*' -> new LexToken(TokenKind.MUL, "*");
            case '/' -> new LexToken(TokenKind.DIV, "/");
            default -> null;
        };
    }

    private List<LexToken> tokenizeLine(String line) {
        List<LexToken> tokens = new ArrayList<>();
        int i = 0;
        int n = line.length();

        while (i < n) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            LexToken fixed = singleCharToken(c);
            if (fixed != null) {
                tokens.add(fixed);
                i++;
                continue;
            }

            if (isKnownDelimiterOrOperator(c)) {
                tokens.add(new LexToken(TokenKind.UNKNOWN_SYMBOL, String.valueOf(c)));
                i++;
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                String word = readUntilStructural(line, i, n);
                i += word.length();
                tokens.add(new LexToken(
                        isValidIdentifier(word) ? TokenKind.IDENTIFIER : TokenKind.INVALID_ELEMENT, word
                ));
                continue;
            }

            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < n && !Character.isWhitespace(line.charAt(i))) {
                    char ch = line.charAt(i);
                    if ((ch == '+' || ch == '-') && i > start
                            && (line.charAt(i - 1) == 'E' || line.charAt(i - 1) == 'e')) {
                        i++;
                        continue;
                    }
                    if (isStructuralChar(ch)) {
                        break;
                    }
                    i++;
                }
                String numLexeme = line.substring(start, i);
                tokens.add(new LexToken(
                        isValidConstant(numLexeme) ? TokenKind.CONSTANT : TokenKind.INVALID_ELEMENT, numLexeme
                ));
                continue;
            }

            String bad = readUntilStructural(line, i, n);
            i += bad.length();
            boolean singleNonAlnum = bad.length() == 1 && !Character.isLetterOrDigit(bad.charAt(0));
            tokens.add(new LexToken(singleNonAlnum ? TokenKind.UNKNOWN_SYMBOL : TokenKind.INVALID_ELEMENT, bad));
        }
        return tokens;
    }

    private String readUntilStructural(String line, int start, int n) {
        int end = start;
        while (end < n && !Character.isWhitespace(line.charAt(end)) && !isStructuralChar(line.charAt(end))) {
            end++;
        }
        return line.substring(start, end);
    }

    private boolean isStructuralChar(char c) {
        return c == ';' || c == '(' || c == ')' || c == '+' || c == '-' || c == '*' || c == '/';
    }

    private boolean isKnownDelimiterOrOperator(char c) {
        return isStructuralChar(c) || c == '[' || c == ']' || c == '{' || c == '}' || c == ',' || c == ':';
    }

    private boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty() || !Character.isLetter(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isLetterOrDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidConstant(String s) {
        return s != null && !s.isEmpty() && s.matches("^\\d+(\\.\\d+)?([Ee][+-]?\\d+)?$");
    }

    private List<List<LexToken>> splitBySemicolon(List<LexToken> tokens) {
        List<List<LexToken>> segments = new ArrayList<>();
        List<LexToken> current = new ArrayList<>();
        for (LexToken tok : tokens) {
            if (tok.kind() == TokenKind.SEMICOLON) {
                segments.add(current);
                current = new ArrayList<>();
            } else {
                current.add(tok);
            }
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }

    private String formatTokens(List<LexToken> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(tokens.get(i).lexeme());
        }
        return sb.toString();
    }

    private List<ParseIssue> runPushdownAutomaton(List<LexToken> tokens, int lineNumber) {
        List<ParseIssue> issues = new ArrayList<>();
        Deque<StackSymbol> stack = new ArrayDeque<>();
        stack.push(StackSymbol.BOTTOM);
        stack.push(StackSymbol.EXPR);

        String lineTag = "Line " + formatLine(lineNumber) + ": ";
        int idx = 0;
        int n = tokens.size();
        int openParenDepth = 0;
        int safetySteps = 0;
        int maxSteps = Math.max(200, n * 30 + 100);

        while (!stack.isEmpty() && safetySteps < maxSteps) {
            safetySteps++;
            StackSymbol top = stack.peek();
            TokenKind lookahead = (idx < n) ? tokens.get(idx).kind() : null;

            if (top == StackSymbol.BOTTOM) {
                if (lookahead == null) {
                    stack.pop();
                    break;
                }
                if (lookahead == TokenKind.RPAREN) {
                    issues.add(new ParseIssue("201", lineTag + "Missing delimiter '(' or extra ')'."));
                } else {
                    issues.add(new ParseIssue("202", lineTag + "Missing operator before '" + tokens.get(idx).lexeme() + "'."));
                }
                idx++;
                continue;
            }

            if (isTerminal(top)) {
                if (top == StackSymbol.RPAREN) {
                    if (lookahead == TokenKind.RPAREN) {
                        idx++;
                    } else {
                        issues.add(new ParseIssue("201", lineTag + "Missing delimiter ')'."));
                    }
                    stack.pop();
                    openParenDepth = Math.max(0, openParenDepth - 1);
                    continue;
                }

                if (lookahead != null && matchesTerminal(top, lookahead)) {
                    if (top == StackSymbol.LPAREN) {
                        openParenDepth++;
                    }
                    stack.pop();
                    idx++;
                } else {
                    if (top == StackSymbol.LPAREN) {
                        issues.add(new ParseIssue("201", lineTag + "Missing delimiter '('."));
                    } else {
                        issues.add(new ParseIssue("202", lineTag + "Missing operator."));
                    }
                    stack.pop();
                }
                continue;
            }

            switch (top) {
                case EXPR -> {
                    stack.pop();
                    if (lookahead == null) {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant."));
                    } else if (isFactorStart(lookahead)) {
                        stack.push(StackSymbol.EXPR_TAIL);
                        stack.push(StackSymbol.TERM);
                    } else if (lookahead == TokenKind.RPAREN) {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant before ')'."));
                    } else if (isBinaryAfterOperand(lookahead)) {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant before '" + tokens.get(idx).lexeme() + "'."));
                        idx++;
                        stack.push(StackSymbol.EXPR);
                    } else {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant near '" + tokens.get(idx).lexeme() + "'."));
                        idx++;
                        stack.push(StackSymbol.EXPR);
                    }
                }
                case EXPR_TAIL -> {
                    stack.pop();
                    if (lookahead == TokenKind.PLUS || lookahead == TokenKind.MINUS) {
                        stack.push(StackSymbol.EXPR_TAIL);
                        stack.push(StackSymbol.TERM);
                        stack.push(lookahead == TokenKind.PLUS ? StackSymbol.PLUS : StackSymbol.MINUS);
                    } else if (lookahead == TokenKind.RPAREN) {
                        if (openParenDepth <= 0) {
                            issues.add(new ParseIssue("201", lineTag + "Unmatched delimiter ')'."));
                            idx++;
                            stack.push(StackSymbol.EXPR_TAIL);
                        }
                    } else if (lookahead != null) {
                        if (isFactorStart(lookahead)) {
                            issues.add(new ParseIssue("202", lineTag + "Missing operator before '" + tokens.get(idx).lexeme() + "'."));
                            stack.push(StackSymbol.EXPR_TAIL);
                            stack.push(StackSymbol.TERM);
                        } else {
                            issues.add(new ParseIssue("202", lineTag + "Missing operator near '" + tokens.get(idx).lexeme() + "'."));
                            idx++;
                            stack.push(StackSymbol.EXPR_TAIL);
                        }
                    }
                }
                case TERM -> {
                    stack.pop();
                    if (lookahead == null) {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant."));
                    } else if (isFactorStart(lookahead)) {
                        stack.push(StackSymbol.TERM_TAIL);
                        stack.push(StackSymbol.FACTOR);
                    } else if (lookahead == TokenKind.RPAREN) {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant before ')'."));
                    } else {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant near '" + tokens.get(idx).lexeme() + "'."));
                        idx++;
                        stack.push(StackSymbol.TERM);
                    }
                }
                case TERM_TAIL -> {
                    stack.pop();
                    if (lookahead == TokenKind.MUL || lookahead == TokenKind.DIV) {
                        stack.push(StackSymbol.TERM_TAIL);
                        stack.push(StackSymbol.FACTOR);
                        stack.push(lookahead == TokenKind.MUL ? StackSymbol.MUL : StackSymbol.DIV);
                    }
                }
                case FACTOR -> {
                    stack.pop();
                    if (lookahead == TokenKind.LPAREN) {
                        stack.push(StackSymbol.RPAREN);
                        stack.push(StackSymbol.EXPR_TAIL);
                        stack.push(StackSymbol.TERM);
                        stack.push(StackSymbol.LPAREN);
                    } else if (lookahead == TokenKind.PLUS || lookahead == TokenKind.MINUS) {
                        stack.push(StackSymbol.FACTOR);
                        stack.push(lookahead == TokenKind.PLUS ? StackSymbol.PLUS : StackSymbol.MINUS);
                    } else if (lookahead == TokenKind.IDENTIFIER || lookahead == TokenKind.CONSTANT) {
                        stack.push(StackSymbol.OPERAND);
                    } else if (lookahead == null) {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant."));
                    } else if (lookahead == TokenKind.RPAREN) {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant before ')'."));
                    } else {
                        issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant before '" + tokens.get(idx).lexeme() + "'."));
                        idx++;
                    }
                }
                default -> stack.pop();
            }
        }

        while (!stack.isEmpty()) {
            if (stack.pop() == StackSymbol.RPAREN) {
                issues.add(new ParseIssue("201", lineTag + "Missing delimiter ')'."));
            }
        }

        while (idx < n) {
            LexToken extra = tokens.get(idx++);
            if (extra.kind() == TokenKind.RPAREN) {
                issues.add(new ParseIssue("201", lineTag + "Missing delimiter '(' or extra ')'."));
            } else if (extra.kind() == TokenKind.LPAREN) {
                issues.add(new ParseIssue("202", lineTag + "Missing operator before '('."));
            } else if (extra.kind() == TokenKind.IDENTIFIER || extra.kind() == TokenKind.CONSTANT) {
                issues.add(new ParseIssue("202", lineTag + "Missing operator before '" + extra.lexeme() + "'."));
            } else {
                issues.add(new ParseIssue("203", lineTag + "Missing identifier/constant near '" + extra.lexeme() + "'."));
            }
        }

        return issues;
    }

    private boolean isTerminal(StackSymbol s) {
        return s == StackSymbol.LPAREN || s == StackSymbol.RPAREN || s == StackSymbol.PLUS
                || s == StackSymbol.MINUS || s == StackSymbol.MUL || s == StackSymbol.DIV
                || s == StackSymbol.OPERAND;
    }

    private boolean matchesTerminal(StackSymbol top, TokenKind lookahead) {
        return switch (top) {
            case LPAREN -> lookahead == TokenKind.LPAREN;
            case RPAREN -> lookahead == TokenKind.RPAREN;
            case PLUS -> lookahead == TokenKind.PLUS;
            case MINUS -> lookahead == TokenKind.MINUS;
            case MUL -> lookahead == TokenKind.MUL;
            case DIV -> lookahead == TokenKind.DIV;
            case OPERAND -> lookahead == TokenKind.IDENTIFIER || lookahead == TokenKind.CONSTANT;
            default -> false;
        };
    }

    private boolean isFactorStart(TokenKind kind) {
        return kind == TokenKind.IDENTIFIER || kind == TokenKind.CONSTANT || kind == TokenKind.LPAREN
                || kind == TokenKind.PLUS || kind == TokenKind.MINUS;
    }

    private boolean isBinaryAfterOperand(TokenKind kind) {
        return kind == TokenKind.MUL || kind == TokenKind.DIV;
    }
}
