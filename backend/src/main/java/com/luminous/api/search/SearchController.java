package com.luminous.api.search;

import com.luminous.api.common.*;
import com.luminous.api.security.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;
    private final SearchRequestGuard guard;
    public SearchController(SearchService service, SearchRequestGuard guard) { this.service = service; this.guard = guard; }
    @GetMapping("/suggest") public ApiResponse<SearchDtos.Suggestions> suggest(@RequestParam String q,
            @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
        var terms = SearchTerms.parse(q); guard.check(user, request);
        return ApiResponse.ok(service.suggest(terms));
    }
    @GetMapping public ApiResponse<SearchDtos.Slice> list(@RequestParam String q, @RequestParam(defaultValue = "posts") String type,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
        var terms = SearchTerms.parse(q); guard.check(user, request);
        return ApiResponse.ok(service.list(terms, type, cursor, size));
    }
}
