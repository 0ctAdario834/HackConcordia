import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Static checks on public/styles.css, cross-checked against public/index.html. */
class StylesheetTest {

    private static final Pattern SELECTOR_TEXT = Pattern.compile("([^{}]+)\\{");
    private static final Pattern CLASS_SELECTOR = Pattern.compile("(?<![\\w.#-])\\.([a-zA-Z_][\\w-]*)");
    private static final Pattern ID_SELECTOR = Pattern.compile("#([a-zA-Z_][\\w-]*)");
    private static final Pattern CLASS_ATTR = Pattern.compile("\\sclass=\"([^\"]+)\"");
    private static final Pattern ID_ATTR = Pattern.compile("\\sid=\"([^\"]+)\"");

    private static String read(String name) throws IOException {
        try (InputStream in = StylesheetTest.class.getResourceAsStream("/public/" + name)) {
            assertNotNull(in, name + " not found on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String stripComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static Set<String> matches(Pattern p, String text) {
        Set<String> out = new HashSet<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Only the selector parts of rules (text before each '{'), so hex colours are ignored. */
    private static String selectorsOnly(String css) {
        StringBuilder sb = new StringBuilder();
        Matcher m = SELECTOR_TEXT.matcher(stripComments(css));
        while (m.find()) {
            sb.append(m.group(1)).append('\n');
        }
        return sb.toString();
    }

    private static Set<String> classesInHtml(String html) {
        Set<String> out = new HashSet<>();
        for (String attr : matches(CLASS_ATTR, html)) {
            for (String c : attr.trim().split("\\s+")) {
                out.add(c);
            }
        }
        return out;
    }

    @Test
    void bracesAreBalanced() throws IOException {
        String css = stripComments(read("styles.css"));
        long open = css.chars().filter(c -> c == '{').count();
        long close = css.chars().filter(c -> c == '}').count();
        assertEquals(open, close, "unbalanced braces in styles.css");
    }

    @Test
    void commentsAreClosed() throws IOException {
        String css = read("styles.css");
        assertEquals(css.split("/\\*", -1).length, css.split("\\*/", -1).length,
                "unclosed or stray CSS comment");
    }

    @Test
    void everyRuleHasDeclarations() throws IOException {
        String css = stripComments(read("styles.css"));
        Matcher m = Pattern.compile("\\{([^{}]*)\\}").matcher(css);
        int rules = 0;
        while (m.find()) {
            rules++;
            assertTrue(m.group(1).contains(":"), "empty or invalid rule body: " + m.group(1).trim());
        }
        assertTrue(rules > 0, "no rules found");
    }

    @Test
    void everyClassSelectorExistsInHtml() throws IOException {
        Set<String> used = matches(CLASS_SELECTOR, selectorsOnly(read("styles.css")));
        Set<String> defined = classesInHtml(read("index.html"));
        for (String cls : used) {
            assertTrue(defined.contains(cls), "CSS targets class not in HTML: ." + cls);
        }
    }

    @Test
    void everyIdSelectorExistsInHtml() throws IOException {
        Set<String> used = matches(ID_SELECTOR, selectorsOnly(read("styles.css")));
        Set<String> defined = matches(ID_ATTR, read("index.html"));
        for (String id : used) {
            assertTrue(defined.contains(id), "CSS targets id not in HTML: #" + id);
        }
    }

    @Test
    void definesResponsiveBreakpoint() throws IOException {
        assertTrue(read("styles.css").contains("@media"), "no media query in styles.css");
    }

    @Test
    void usesBoxSizingReset() throws IOException {
        assertTrue(stripComments(read("styles.css")).contains("box-sizing: border-box"));
    }
}
