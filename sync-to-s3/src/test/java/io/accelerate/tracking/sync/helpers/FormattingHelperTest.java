package io.accelerate.tracking.sync.helpers;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FormattingHelperTest {

    // Tests for stripLeadingSlash()

    @Test
    void stripLeadingSlash_shouldRemoveLeadingSlash() {
        String result = FormattingHelper.stripLeadingSlash("/example");
        assertThat(result, is("example"));
    }

    @Test
    void stripLeadingSlash_shouldReturnSameStringIfNoLeadingSlash() {
        String result = FormattingHelper.stripLeadingSlash("example");
        assertThat(result, is("example"));
    }

    @Test
    void stripLeadingSlash_shouldReturnNullIfInputIsNull() {
        String result = FormattingHelper.stripLeadingSlash(null);
        assertThat(result, is(nullValue()));
    }

    @Test
    void stripLeadingSlash_shouldReturnEmptyStringIfInputIsEmpty() {
        String result = FormattingHelper.stripLeadingSlash("");
        assertThat(result, is(""));
    }

    @Test
    void stripLeadingSlash_shouldReturnEmptyStringIfInputIsSingleSlash() {
        String result = FormattingHelper.stripLeadingSlash("/");
        assertThat(result, is(""));
    }

    @Test
    void stripLeadingSlash_shouldRemoveOnlyTheFirstLeadingSlash() {
        String result = FormattingHelper.stripLeadingSlash("///example");
        assertThat(result, is("//example"));
    }

    // Tests for sanitizeETag()

    @Test
    void sanitizeETag_shouldRemoveQuotesFromBothEnds() {
        String result = FormattingHelper.sanitizeETag("\"exampleETag\"");
        assertThat(result, is("exampleETag"));
    }

    @Test
    void sanitizeETag_shouldReturnSameStringIfNoQuotesPresent() {
        String result = FormattingHelper.sanitizeETag("exampleETag");
        assertThat(result, is("exampleETag"));
    }

    @Test
    void sanitizeETag_shouldReturnNullIfInputIsNull() {
        String result = FormattingHelper.sanitizeETag(null);
        assertThat(result, is(nullValue()));
    }

    @Test
    void sanitizeETag_shouldReturnEmptyStringIfInputIsEmpty() {
        String result = FormattingHelper.sanitizeETag("");
        assertThat(result, is(""));
    }

    @Test
    void sanitizeETag_shouldKeepMismatchedOpeningQuote() {
        String result = FormattingHelper.sanitizeETag("\"mismatched");
        assertThat(result, is("\"mismatched"));
    }

    @Test
    void sanitizeETag_shouldKeepMismatchedClosingQuote() {
        String result = FormattingHelper.sanitizeETag("mismatched\"");
        assertThat(result, is("mismatched\""));
    }

    @Test
    void sanitizeETag_shouldTrimWhitespacesAndQuotes() {
        String result = FormattingHelper.sanitizeETag("  \"trimmed\"  ");
        assertThat(result, is("trimmed"));
    }

    // Tests for buildKey()

    @Test
    void buildKey_shouldReturnKeyWithPrefixAndFileName() {
        String result = FormattingHelper.buildKey("prefix", "example.txt");
        assertThat(result, is("prefix/example.txt"));
    }

    @Test
    void buildKey_shouldAppendFileNameToPrefixEndingWithSlash() {
        String result = FormattingHelper.buildKey("prefix/", "example.txt");
        assertThat(result, is("prefix/example.txt"));
    }

    @Test
    void buildKey_shouldStripLeadingSlashFromFileName() {
        String result = FormattingHelper.buildKey("prefix", "/example.txt");
        assertThat(result, is("prefix/example.txt"));
    }

    @Test
    void buildKey_shouldReturnFileNameIfPrefixIsEmpty() {
        String result = FormattingHelper.buildKey("", "/example.txt");
        assertThat(result, is("example.txt"));
    }

    @Test
    void buildKey_shouldReturnFileNameIfPrefixIsEmptyAndNoLeadingSlashPresent() {
        String result = FormattingHelper.buildKey("", "example.txt");
        assertThat(result, is("example.txt"));
    }

    @Test
    void buildKey_shouldThrowNullPointerExceptionIfPrefixIsNull() {
        try {
            FormattingHelper.buildKey(null, "example.txt");
        } catch (NullPointerException e) {
            assertThat(e.getClass(), equalTo(NullPointerException.class));
        }
    }

    @Test
    void buildKey_shouldThrowNullPointerExceptionIfFileNameIsNull() {
        try {
            FormattingHelper.buildKey("prefix", null);
        } catch (NullPointerException e) {
            assertThat(e.getClass(), equalTo(NullPointerException.class));
        }
    }
}