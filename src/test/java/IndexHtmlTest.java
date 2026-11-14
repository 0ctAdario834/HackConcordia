import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Static checks on public/index.html, read straight from the classpath. */
class IndexHtmlTest {

    private static final Pattern ID_ATTR = Pattern.compile("\\sid=\"([^\"]+)\"");
    private static final Pattern FOR_ATTR = Pattern.compile("\\sfor=\"([^\"]+)\"");
    private static final Pattern REF_ATTR = Pattern.compile("\\s(?:href|src)=\"([^\"]+)\"");
    private static final Pattern CHECKBOX = Pattern.compile("<input[^>]*type=\"checkbox\"[^>]*>");
    private static final Pattern CHECKBOX_ID = Pattern.compile("\\sid=\"([^\"]+)\"");

    private static String html;

    static String loadHtml() throws IOException {
        if (html == null) {
            try (InputStream in = IndexHtmlTest.class.getResourceAsStream("/public/index.html")) {
                assertNotNull(in, "index.html not found on classpath");
                html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return html;
    }

    private static Set<String> allMatches(Pattern p, String text) {
        Set<String> out = new HashSet<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Counts whole-tag occurrences, so "<head" does not match "<header". */
    private static int countTags(String text, String tag, boolean closing) {
        Pattern p = Pattern.compile("<" + (closing ? "/" : "") + tag + "(?=[\\s>/])");
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    // --- document skeleton -------------------------------------------------

    @Test
    void hasDoctype() throws IOException {
        assertTrue(loadHtml().trim().toLowerCase().startsWith("<!doctype html>"));
    }

    @Test
    void declaresLanguage() throws IOException {
        assertTrue(loadHtml().matches("(?s).*<html[^>]*\\blang=\"[a-zA-Z-]+\".*"));
    }

    @Test
    void declaresUtf8Charset() throws IOException {
        assertTrue(loadHtml().toLowerCase().contains("charset=\"utf-8\""));
    }

    @Test
    void declaresViewport() throws IOException {
        assertTrue(loadHtml().contains("name=\"viewport\""));
    }

    @Test
    void hasNonEmptyTitle() throws IOException {
        Matcher m = Pattern.compile("<title>([^<]*)</title>").matcher(loadHtml());
        assertTrue(m.find(), "no <title> element");
        assertFalse(m.group(1).isBlank(), "<title> is empty");
    }

    @Test
    void hasSingleLandmarkElements() throws IOException {
        String h = loadHtml();
        for (String tag : List.of("html", "head", "body", "header", "main", "footer")) {
            assertEquals(1, countTags(h, tag, false), "expected exactly one <" + tag + ">");
            assertEquals(1, countTags(h, tag, true), "expected exactly one </" + tag + ">");
        }
    }

    // --- interactive elements ----------------------------------------------

    @Test
    void searchInputIsSearchType() throws IOException {
        assertTrue(loadHtml().matches("(?s).*<input[^>]*type=\"search\"[^>]*id=\"search\".*")
                || loadHtml().matches("(?s).*<input[^>]*id=\"search\"[^>]*type=\"search\".*"));
    }

    @Test
    void hasAtLeastOneTagCheckbox() throws IOException {
        Matcher m = CHECKBOX.matcher(loadHtml());
        int n = 0;
        while (m.find()) {
            n++;
        }
        assertTrue(n >= 1, "expected at least one tag checkbox");
    }

    @Test
    void headerFilterValuesAreRealTagNames() throws IOException {
        String h = loadHtml();
        for (String tag : new String[] {"Study", "Library", "Café", "Cafeteria"}) {
            assertTrue(h.contains("<input type=\"checkbox\" id=\"tag") && h.contains("value=\"" + tag + "\""),
                    "header filter missing value " + tag);
        }
        assertFalse(h.contains("value=\"tag1\""), "header filter still uses placeholder values");
    }

    @Test
    void everyCheckboxHasUniqueId() throws IOException {
        Matcher m = CHECKBOX.matcher(loadHtml());
        Set<String> ids = new HashSet<>();
        while (m.find()) {
            Matcher idMatcher = CHECKBOX_ID.matcher(m.group());
            assertTrue(idMatcher.find(), "checkbox without id: " + m.group());
            assertTrue(ids.add(idMatcher.group(1)), "duplicate checkbox id: " + idMatcher.group(1));
        }
    }

    @Test
    void everyCheckboxHasMatchingLabel() throws IOException {
        String h = loadHtml();
        Set<String> labelTargets = allMatches(FOR_ATTR, h);
        Matcher m = CHECKBOX.matcher(h);
        while (m.find()) {
            String id = allMatches(CHECKBOX_ID, m.group()).iterator().next();
            assertTrue(labelTargets.contains(id), "no <label for=\"" + id + "\">");
        }
    }

    @Test
    void addPlaceButtonExists() throws IOException {
        assertTrue(loadHtml().matches("(?s).*<button[^>]*type=\"button\"[^>]*id=\"add-place\".*")
                || loadHtml().matches("(?s).*<button[^>]*id=\"add-place\"[^>]*type=\"button\".*"));
    }

    @Test
    void resultsListAndAtLeastOneResultExist() throws IOException {
        String h = loadHtml();
        assertTrue(h.contains("class=\"results\""), "missing .results list");
        assertTrue(h.contains("class=\"result\""), "missing .result item");
    }

    // --- add-place dialog ---------------------------------------------------

    @Test
    void addPlaceDialogExists() throws IOException {
        assertTrue(loadHtml().contains("<dialog id=\"add-place-dialog\">"));
    }

    @Test
    void addPlaceFormHasRequiredNameInput() throws IOException {
        String h = loadHtml();
        assertTrue(h.matches("(?s).*<input[^>]*id=\"place-name\"[^>]*required.*")
                || h.matches("(?s).*<input[^>]*required[^>]*id=\"place-name\".*"),
                "place-name input must be required");
    }

    @Test
    void addPlaceFormHasDescriptionField() throws IOException {
        assertTrue(loadHtml().contains("<textarea id=\"place-description\""));
    }

    @Test
    void addPlaceFormOffersAllTags() throws IOException {
        String h = loadHtml();
        for (String tag : new String[] {"Study", "Library", "Café", "Cafeteria"}) {
            assertTrue(h.contains("name=\"tags\" value=\"" + tag + "\""), "no form checkbox for tag " + tag);
        }
    }

    @Test
    void addPlaceFormHasSaveAndCancelButtons() throws IOException {
        String h = loadHtml();
        assertTrue(h.contains("id=\"save-place\""));
        assertTrue(h.contains("id=\"cancel-place\""));
    }

    @Test
    void resultTemplateExistsForRendering() throws IOException {
        assertTrue(loadHtml().contains("<template id=\"place-template\">"));
    }

    @Test
    void resultsContainerIsEmptyForScriptToFill() throws IOException {
        assertTrue(loadHtml().contains("<ul class=\"results\" id=\"results\"></ul>"));
    }

    @Test
    void scriptIsLoadedLast() throws IOException {
        String h = loadHtml();
        assertTrue(h.contains("<script src=\"app.js\"></script>"));
        assertTrue(h.indexOf("<script") > h.indexOf("</footer>"), "script should come after page content");
    }

    // --- integrity ----------------------------------------------------------

    @Test
    void idsAreUnique() throws IOException {
        Matcher m = ID_ATTR.matcher(loadHtml());
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            assertTrue(seen.add(m.group(1)), "duplicate id: " + m.group(1));
        }
    }

    @Test
    void everyLabelTargetsExistingId() throws IOException {
        String h = loadHtml();
        Set<String> ids = allMatches(ID_ATTR, h);
        for (String target : allMatches(FOR_ATTR, h)) {
            assertTrue(ids.contains(target), "label for missing id: " + target);
        }
    }

    @Test
    void everyLocalReferenceExistsInPublicFolder() throws Exception {
        Path publicDir = Path.of(IndexHtmlTest.class.getResource("/public/index.html").toURI()).getParent();
        for (String ref : allMatches(REF_ATTR, loadHtml())) {
            if (ref.contains("://") || ref.startsWith("#") || ref.startsWith("mailto:")
                    || ref.startsWith("data:") || ref.startsWith("//")) {
                continue;
            }
            String clean = ref.startsWith("/") ? ref.substring(1) : ref;
            assertTrue(Files.exists(publicDir.resolve(clean)), "referenced file missing: " + ref);
        }
    }

    @Test
    void noEmptyHrefOrSrc() throws IOException {
        assertFalse(loadHtml().contains("href=\"\""));
        assertFalse(loadHtml().contains("src=\"\""));
    }
}
