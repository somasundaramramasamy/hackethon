package com.cognizant.backend.scenario;

import java.util.List;

public record ImportResult(int created, int updated, List<String> codes) {
}
