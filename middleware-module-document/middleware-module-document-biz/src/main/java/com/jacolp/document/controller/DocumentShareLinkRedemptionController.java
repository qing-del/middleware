package com.jacolp.document.controller;

import com.jacolp.common.core.result.Result;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;
import com.jacolp.document.application.share.DocumentShareLinkRedemptionService;
import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Public navigation and authenticated redemption endpoints for document share links. */
@RestController
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentShareLinkRedemptionController {

    private final DocumentShareLinkRedemptionService redemptionService;
    private final String frontendBaseUrl;

    public DocumentShareLinkRedemptionController(DocumentShareLinkRedemptionService redemptionService,
                                                 @Value("${jacolp.base-url}") String frontendBaseUrl) {
        this.redemptionService = Objects.requireNonNull(redemptionService);
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) throw new IllegalStateException("jacolp.base-url is required");
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        HttpHeaders headers = new HttpHeaders();
        // Treat the code as a path segment so it cannot alter the configured destination.
        URI destination = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .pathSegment("share", "documents", code)
                .build().encode().toUri();
        headers.setLocation(destination);
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Referrer-Policy", "no-referrer");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @PostMapping("/user/document/share-links/{code}/redeem")
    public Result<DocumentShareLinkRedeemResponse> redeem(@PathVariable String code) {
        CurrentPrincipal principal = new SecurityContextCurrentPrincipalAccessor().currentPrincipal()
                .orElseThrow(() -> new com.jacolp.common.core.exception.AuthenticationException("当前登录信息已失效"));
        return Result.success(redemptionService.redeem(principal, code));
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
