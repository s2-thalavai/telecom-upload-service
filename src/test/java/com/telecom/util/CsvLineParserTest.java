package com.telecom.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvLineParserTest {

    @Test
    void splitsSimpleLine() {
        assertThat(CsvLineParser.parse("Arun,arun@example.com,9876543210,PREPAID"))
                .containsExactly("Arun", "arun@example.com", "9876543210", "PREPAID");
    }

    @Test
    void keepsCommasInsideQuotes() {
        assertThat(CsvLineParser.parse("\"Rao, Venkat\",v@example.com,9000012345,POSTPAID"))
                .containsExactly("Rao, Venkat", "v@example.com", "9000012345", "POSTPAID");
    }

    @Test
    void unescapesDoubledQuotes() {
        assertThat(CsvLineParser.parse("\"Say \"\"hi\"\"\",x")).containsExactly("Say \"hi\"", "x");
    }

    @Test
    void preservesEmptyFields() {
        assertThat(CsvLineParser.parse("a,,c,")).containsExactly("a", "", "c", "");
    }

    @Test
    void singleFieldLine() {
        assertThat(CsvLineParser.parse("only")).containsExactly("only");
    }
}
