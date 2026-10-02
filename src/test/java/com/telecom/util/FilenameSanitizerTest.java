package com.telecom.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizerTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "id-card.pdf                 | id-card.pdf",
            "../../etc/passwd            | passwd",
            "C:\\Users\\siva\\bill.pdf   | bill.pdf",
            "/var/tmp/photo.png          | photo.png",
            "my bill (march).pdf         | my bill (march).pdf",
            "what?<is>this*.pdf          | what__is_this_.pdf",
            "'  spaced.pdf'              | spaced.pdf"
    })
    void sanitizesPathsAndUnsafeCharacters(String input, String expected) {
        assertThat(FilenameSanitizer.sanitize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ".", "..", "../", "/"})
    void fallsBackWhenNothingUsableRemains(String input) {
        assertThat(FilenameSanitizer.sanitize(input)).isEqualTo(FilenameSanitizer.FALLBACK);
    }

    @Test
    void replacesControlCharacters() {
        assertThat(FilenameSanitizer.sanitize("bad\nname\t.pdf")).isEqualTo("bad_name_.pdf");
    }

    @Test
    void truncatesLongNamesKeepingTheExtension() {
        String longName = "a".repeat(400) + ".pdf";

        String result = FilenameSanitizer.sanitize(longName);

        assertThat(result).hasSize(FilenameSanitizer.MAX_LENGTH).endsWith(".pdf");
    }
}
