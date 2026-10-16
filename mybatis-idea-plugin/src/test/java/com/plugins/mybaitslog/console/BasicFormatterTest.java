package com.plugins.mybaitslog.console;

/** 验证常见 SQL 排版及字面量、注释的完整性，可通过 main 直接执行。 */
public class BasicFormatterTest {
    /** 对排版结果和需要原样保留的 SQL 运行回归验证。 */
    public static void main(String[] args) {
        BasicFormatter formatter = new BasicFormatter();
        String newline = System.lineSeparator();
        String formatted = formatter.format("select   o.id, o.name from orders o where o.status=1 and o.name='Case Sensitive'");
        String expected = "SELECT\n    o.id,\n    o.name\nFROM\n    orders o\nWHERE\n    o.status=1\n    AND o.name='Case Sensitive'".replace("\n", newline);
        if (!expected.equals(formatted)) {
            throw new AssertionError("字段、条件或缩进排版不符合预期:\n" + formatted);
        }
        String[][] cases = {
                {"select coalesce(a,b), c from t", "coalesce(a,b),"},
                {"select `from`, `Order` from `TableName`", "`from`,"},
                {"select [from], [Order] from [TableName]", "[from],"},
                {"select \"from\" from \"TableName\"", "\"from\""},
                {"select 'it''s from here' from t", "'it''s from here'"},
                {"select 'a\nb\tc' from t", "'a\nb\tc'"},
                {"update t set a=1,b=2 where id=3", "SET"},
                {"insert into t(a,b) values(1,2)", "VALUES"},
                {"delete from t where id=3", "DELETE"},
                {"select id from t where id in (select id from u)", "SELECT"},
                {"select a from t left join u on t.id=u.id where u.id=1", "LEFT JOIN"},
                {"select distinct a as name from t order by a desc limit 10 offset 20", "DESC"},
                {"select t.id from t left join u on coalesce(t.a,t.b)=u.id", "coalesce(t.a,t.b)"}
        };
        for (String[] item : cases) {
            String actual = formatter.format(item[0]);
            if (!actual.contains(item[1]) || actual.matches("(?s).*[ \\t](?:\\r?\\n).*")) {
                throw new AssertionError("排版丢失内容或存在行尾空格: " + item[0] + "\n" + actual);
            }
        }
        String[] unchanged = {
                "select 1 -- comment\nfrom t", "select /* hint */ 1 from t",
                "select 1 # comment\nfrom t", "select $$from\nwhere$$ from t",
                "select $body$from\nwhere$body$ from t", "select 'a\\'b' from t",
                "select [a]]from] from t", "select '"
        };
        for (String sql : unchanged) {
            if (!sql.equals(formatter.format(sql))) {
                throw new AssertionError("特殊语法或不完整 SQL 应保留原文: " + sql);
            }
        }
        if (!"".equals(formatter.format(null)) || !"".equals(formatter.format(" \t\n"))) {
            throw new AssertionError("空 SQL 应输出空串");
        }
        System.out.println("PASS: 24 SQL formatting cases");
    }
}
