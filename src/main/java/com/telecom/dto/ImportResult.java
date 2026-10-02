package com.telecom.dto;

import java.util.List;

public record ImportResult(int totalRows, int imported, int failed, List<RowError> errors) {
}
