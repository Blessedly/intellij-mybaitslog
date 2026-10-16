package com.plugins.mybaitslog.console;


/*
 * Original class is here:
 * https://github.com/hibernate/hibernate-orm/blob/a30635f14ae272fd63a653f9a9e1a9aeb390fad4/hibernate-core/src/main/java/org/hibernate/engine/jdbc/internal/BasicFormatterImpl.java
 *
 * it imported org.hibernate.internal.util.StringHelper, but it doesn't really need that.
 */

import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Performs formatting of basic SQL statements (DML + query).
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public class BasicFormatter {

    // MOD: from org.hibernate.internal.util.StringHelper
    private static final String WHITESPACE = " \n\r\f\t";

    private static final Set<String> BEGIN_CLAUSES = new HashSet<String>();
    private static final Set<String> END_CLAUSES = new HashSet<String>();
    private static final Set<String> LOGICAL = new HashSet<String>();
    private static final Set<String> QUANTIFIERS = new HashSet<String>();
    private static final Set<String> DML = new HashSet<String>();
    private static final Set<String> MISC = new HashSet<String>();

    static {
        BEGIN_CLAUSES.add("left");
        BEGIN_CLAUSES.add("right");
        BEGIN_CLAUSES.add("inner");
        BEGIN_CLAUSES.add("outer");
        BEGIN_CLAUSES.add("group");
        BEGIN_CLAUSES.add("order");

        END_CLAUSES.add("where");
        END_CLAUSES.add("set");
        END_CLAUSES.add("having");
        END_CLAUSES.add("join");
        END_CLAUSES.add("from");
        END_CLAUSES.add("by");
        END_CLAUSES.add("into");
        END_CLAUSES.add("union");
        END_CLAUSES.add("limit");
        END_CLAUSES.add("offset");

        LOGICAL.add("and");
        LOGICAL.add("or");
        LOGICAL.add("when");
        LOGICAL.add("else");
        LOGICAL.add("end");

        QUANTIFIERS.add("in");
        QUANTIFIERS.add("all");
        QUANTIFIERS.add("exists");
        QUANTIFIERS.add("some");
        QUANTIFIERS.add("any");

        DML.add("insert");
        DML.add("update");
        DML.add("delete");

        MISC.add("select");
        MISC.add("on");
        Collections.addAll(MISC, "as", "distinct", "case", "between", "is", "not", "null", "like", "asc", "desc");
    }

    private static final String INDENT_STRING = "    ";
    // MOD: Make initial indent zero
    /*System.lineSeparator() + INDENT_STRING*/
    private static final String INITIAL = "";

    /** 按 SQL 子句分行缩进；无法安全识别的语法保留原文。 */
    public String format(String source) {
        if (source == null) {
            return "";
        }
        // shortcut: 注释、美元引用和特殊转义保留原文，需要排版这些语法时接入方言解析器。
        if (source.contains("--") || source.contains("/*") || source.contains("#")
                || source.contains("\\") || source.contains("]]")
                || source.matches("(?s).*\\$(?:[a-zA-Z_][a-zA-Z_0-9]*)?\\$.*")) {
            return source;
        }
        try {
            return new FormatProcess(source).perform().trim();
        } catch (NoSuchElementException ex) {
            // 不完整的日志 SQL 不应导致整条日志无法输出。
            return source;
        }
    }

    private static class FormatProcess {
        boolean beginLine = true;
        boolean afterBeginBeforeEnd;
        boolean afterByOrSetOrFromOrSelect;
        boolean afterValues;
        boolean afterOn;
        boolean afterBetween;
        boolean afterInsert;
        int inFunction;
        int parensSinceSelect;
        private LinkedList<Integer> parenCounts = new LinkedList<Integer>();
        private LinkedList<Boolean> afterByOrFromOrSelects = new LinkedList<Boolean>();

        // MOD: Make BOL indent zero
        int indent = /*1*/0;

        StringBuilder result = new StringBuilder();
        StringTokenizer tokens;
        String lastToken;
        String token;
        String lcToken;

        public FormatProcess(String sql) {
            tokens = new StringTokenizer(
                    sql,
                    "()+*/-=<>'`\"[]," + /*StringHelper.*/WHITESPACE,
                    true
            );
        }

        public String perform() {

            result.append(INITIAL);

            while (tokens.hasMoreTokens()) {
                token = tokens.nextToken();
                lcToken = token.toLowerCase(Locale.ROOT);

                if ("'".equals(token)) {
                    String t;
                    do {
                        t = tokens.nextToken();
                        token += t;
                    }
                    // cannot handle single quotes
                    while (!"'".equals(t) && tokens.hasMoreTokens());
                } else if ("\"".equals(token) || "`".equals(token)) {
                    final String quote = token;
                    String t;
                    do {
                        t = tokens.nextToken();
                        token += t;
                    }
                    while (!quote.equals(t) && tokens.hasMoreTokens());
                }
                // SQL Server uses "[" and "]" to escape reserved words
                // see SQLServerDialect.openQuote and SQLServerDialect.closeQuote
                else if ("[".equals(token)) {
                    String t;
                    do {
                        t = tokens.nextToken();
                        token += t;
                    }
                    while (!"]".equals(t) && tokens.hasMoreTokens());
                }

                if (afterByOrSetOrFromOrSelect && inFunction == 0 && parensSinceSelect == 0
                        && ",".equals(token)) {
                    commaAfterByOrFromOrSelect();
                } else if (afterOn && inFunction == 0 && parensSinceSelect == 0 && ",".equals(token)) {
                    commaAfterOn();
                } else if ("(".equals(token)) {
                    openParen();
                } else if (")".equals(token)) {
                    closeParen();
                } else if (BEGIN_CLAUSES.contains(lcToken)) {
                    beginNewClause();
                } else if (END_CLAUSES.contains(lcToken)) {
                    endNewClause();
                } else if ("select".equals(lcToken)) {
                    select();
                } else if (DML.contains(lcToken)) {
                    updateOrInsertOrDelete();
                } else if ("values".equals(lcToken)) {
                    values();
                } else if ("on".equals(lcToken)) {
                    on();
                } else if (afterBetween && lcToken.equals("and")) {
                    misc();
                    afterBetween = false;
                } else if (LOGICAL.contains(lcToken)) {
                    logical();
                } else if (isWhitespace(token)) {
                    white();
                } else {
                    misc();
                }

                if (!isWhitespace(token)) {
                    lastToken = lcToken;
                }

            }
            return result.toString();
        }

        private void commaAfterOn() {
            out();
            indent--;
            newline();
            afterOn = false;
            afterByOrSetOrFromOrSelect = true;
        }

        private void commaAfterByOrFromOrSelect() {
            out();
            newline();
        }

        private void logical() {
            if ("end".equals(lcToken)) {
                indent--;
            }
            newline();
            out();
            beginLine = false;
        }

        private void on() {
            indent++;
            afterOn = true;
            newline();
            out();
            beginLine = false;
        }

        private void misc() {
            out();
            if ("between".equals(lcToken)) {
                afterBetween = true;
            }
            if (afterInsert) {
                newline();
                afterInsert = false;
            } else {
                beginLine = false;
                if ("case".equals(lcToken)) {
                    indent++;
                }
            }
        }

        private void white() {
            if (!beginLine && result.length() > 0 && result.charAt(result.length() - 1) != ' ') {
                result.append(" ");
            }
        }

        private void updateOrInsertOrDelete() {
            out();
            indent++;
            beginLine = false;
            if ("update".equals(lcToken)) {
                newline();
            }
            if ("insert".equals(lcToken)) {
                afterInsert = true;
            }
        }

        private void select() {
            out();
            indent++;
            newline();
            parenCounts.addLast(parensSinceSelect);
            afterByOrFromOrSelects.addLast(afterByOrSetOrFromOrSelect);
            parensSinceSelect = 0;
            afterByOrSetOrFromOrSelect = true;
        }

        private void out() {
            // 只转换已识别的结构关键字，保留字段名和引号内文本的大小写。
            if (BEGIN_CLAUSES.contains(lcToken) || END_CLAUSES.contains(lcToken)
                    || LOGICAL.contains(lcToken) || DML.contains(lcToken)
                    || MISC.contains(lcToken) || QUANTIFIERS.contains(lcToken) || "values".equals(lcToken)) {
                result.append(token.toUpperCase(Locale.ROOT));
            } else {
                result.append(token);
            }
        }

        private void endNewClause() {
            if (!afterBeginBeforeEnd) {
                indent--;
                if (afterOn) {
                    indent--;
                    afterOn = false;
                }
                newline();
            }
            out();
            if (!"union".equals(lcToken)) {
                indent++;
            }
            newline();
            afterBeginBeforeEnd = false;
            afterByOrSetOrFromOrSelect = "by".equals(lcToken)
                    || "set".equals(lcToken)
                    || "from".equals(lcToken);
        }

        private void beginNewClause() {
            if (!afterBeginBeforeEnd) {
                if (afterOn) {
                    indent--;
                    afterOn = false;
                }
                indent--;
                newline();
            }
            out();
            beginLine = false;
            afterBeginBeforeEnd = true;
        }

        private void values() {
            indent--;
            newline();
            out();
            indent++;
            newline();
            afterValues = true;
        }

        private void closeParen() {
            parensSinceSelect--;
            if (parensSinceSelect < 0) {
                indent--;
                parensSinceSelect = parenCounts.removeLast();
                afterByOrSetOrFromOrSelect = afterByOrFromOrSelects.removeLast();
            }
            if (inFunction > 0) {
                inFunction--;
                out();
            } else {
                if (!afterByOrSetOrFromOrSelect) {
                    indent--;
                    newline();
                }
                out();
            }
            beginLine = false;
        }

        private void openParen() {
            if (isFunctionName(lastToken) || inFunction > 0) {
                inFunction++;
            }
            beginLine = false;
            if (inFunction > 0) {
                out();
            } else {
                out();
                if (!afterByOrSetOrFromOrSelect) {
                    indent++;
                    newline();
                    beginLine = true;
                }
            }
            parensSinceSelect++;
        }

        private static boolean isFunctionName(String tok) {
            if (tok == null || tok.length() == 0) {
                return false;
            }

            final char begin = tok.charAt(0);
            final boolean isIdentifier = Character.isJavaIdentifierStart(begin) || '"' == begin;
            return isIdentifier &&
                    !LOGICAL.contains(tok) &&
                    !END_CLAUSES.contains(tok) &&
                    !QUANTIFIERS.contains(tok) &&
                    !DML.contains(tok) &&
                    !MISC.contains(tok);
        }

        private static boolean isWhitespace(String token) {
            return /*StringHelper.*/WHITESPACE.contains(token);
        }

        private void newline() {
            // 清理行尾空格，避免字段列表与条件之间出现多余空白。
            while (result.length() > 0 && result.charAt(result.length() - 1) == ' ') {
                result.setLength(result.length() - 1);
            }
            result.append(System.lineSeparator());
            for (int i = 0; i < indent; i++) {
                result.append(INDENT_STRING);
            }
            beginLine = true;
        }
    }
}
