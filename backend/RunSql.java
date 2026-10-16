import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Runs a .sql file against the real database and reports the FIRST failing
 * statement, with the statement text and its position in the file.
 *
 * WHY: Flyway reports "ERROR: <message>" but not which statement caused
 * it. A failed migration then costs a 15-second application restart per
 * attempt. This gives feedback in about a second.
 *
 * Single connection, autocommit off, so TEMP tables survive between
 * statements — exactly like Flyway. With --dry-run the whole thing is
 * rolled back, so the script can be validated without touching state.
 *
 *   java RunSql.java file.sql --dry-run
 */
public class RunSql {

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        List<String> files = new ArrayList<>();

        for (String a : args) {
            if ("--dry-run".equals(a)) dryRun = true;
            else files.add(a);
        }

        if (files.isEmpty()) {
            System.out.println("usage: RunSql <file.sql> [more.sql ...] [--dry-run]");
            System.exit(2);
        }

        try (Connection c = DriverManager.getConnection(
                System.getenv("DB_URL"),
                System.getenv("DB_USERNAME"),
                System.getenv("DB_PASSWORD"))) {

            // One transaction across every file, so migrations that depend
            // on each other can be validated together exactly as Flyway
            // will apply them.
            c.setAutoCommit(false);

            int total = 0;
            for (String file : files) {
                String text = Files.readString(Path.of(file));
                List<String> statements = split(text);
                int[] offsets = offsets(text, statements);

                System.out.println();
                System.out.println("=== " + Path.of(file).getFileName()
                        + " : " + statements.size() + " statements ===");

                for (int i = 0; i < statements.size(); i++) {
                    total++;
                    String sql = statements.get(i);
                    try (Statement s = c.createStatement()) {
                        s.execute(sql);
                        System.out.println("  [" + (i + 1) + "] OK");
                    } catch (SQLException ex) {
                        System.out.println();
                        System.out.println("!!! FAILED in " + Path.of(file).getFileName()
                                + " at statement " + (i + 1) + " of " + statements.size()
                                + "  (file char offset ~" + offsets[i] + ")");
                        System.out.println("!!! " + ex.getMessage());
                        System.out.println("--- statement ---");
                        System.out.println(sql.length() > 1000 ? sql.substring(0, 1000) + " ..." : sql);
                        c.rollback();
                        System.exit(1);
                    }
                }
            }

            if (dryRun) {
                c.rollback();
                System.out.println();
                System.out.println("All " + total + " statements succeeded. ROLLED BACK - nothing was changed.");
            } else {
                c.commit();
                System.out.println();
                System.out.println("All " + total + " statements committed.");
            }
        }
    }

    /** Character offset of each statement, so the error can be located. */
    static int[] offsets(String text, List<String> statements) {
        int[] out = new int[statements.size()];
        int cursor = 0;
        for (int i = 0; i < statements.size(); i++) {
            int at = text.indexOf(statements.get(i), cursor);
            out[i] = at < 0 ? -1 : at;
            cursor = (at < 0 ? cursor : at) + statements.get(i).length();
        }
        return out;
    }

    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false, inDollar = false;
        String dollarTag = null;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (inDollar) {
                cur.append(ch);
                if (text.startsWith(dollarTag, i)) {
                    cur.append(text, i + 1, i + dollarTag.length());
                    i += dollarTag.length() - 1;
                    inDollar = false;
                }
                continue;
            }
            if (!inSingle && !inDouble && ch == '$') {
                int end = text.indexOf('$', i + 1);
                if (end > i) {
                    dollarTag = text.substring(i, end + 1);
                    inDollar = true;
                    cur.append(text, i, end + 1);
                    i = end;
                    continue;
                }
            }
            if (ch == '\'' && !inDouble) inSingle = !inSingle;
            else if (ch == '"' && !inSingle) inDouble = !inDouble;

            // Skip line comments entirely, so a semicolon inside a "--"
            // comment cannot terminate a statement early. Comments are
            // dropped rather than kept: the database does not need them
            // and this avoids any chance of them confusing the parser.
            if (!inSingle && !inDouble && ch == '-' && i + 1 < text.length()
                    && text.charAt(i + 1) == '-') {
                while (i < text.length() && text.charAt(i) != '\n') i++;
                cur.append('\n');
                continue;
            }

            cur.append(ch);
            if (ch == ';' && !inSingle && !inDouble && !inDollar) {
                String stmt = cur.toString().trim();
                if (isMeaningful(stmt)) out.add(stmt);
                cur.setLength(0);
            }
        }
        String tail = cur.toString().trim();
        if (isMeaningful(tail)) out.add(tail);
        return out;
    }

    static boolean isMeaningful(String s) {
        return !s.trim().isEmpty();
    }
}