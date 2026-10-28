package com.suwasarana.api.ivr;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Twilio voice webhook. Every call must carry a valid {@code X-Twilio-Signature} (see {@link TwilioSignatureValidator})
 * unless {@code app.ivr.signature-required=false} is set for local development.
 */
@RestController
@RequestMapping("/api/ivr")
public class IvrController {

    private static final Logger log = LoggerFactory.getLogger(IvrController.class);
    private static final String EMPTY_RESPONSE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Response/>";

    private final IvrService ivrService;
    private final TwilioSignatureValidator signatureValidator;
    private final IvrProperties properties;

    @Autowired
    public IvrController(IvrService ivrService, TwilioSignatureValidator signatureValidator, IvrProperties properties) {
        this.ivrService = ivrService;
        this.signatureValidator = signatureValidator;
        this.properties = properties;
    }

    @PostMapping(value = "/webhook", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleTwilioWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestParam(value = "Digits", required = false) String digits) {
        return handle(request, signature, digits);
    }

    @GetMapping(value = "/webhook", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleTwilioWebhookGet(
            HttpServletRequest request,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestParam(value = "Digits", required = false) String digits) {
        return handle(request, signature, digits);
    }

    private ResponseEntity<String> handle(HttpServletRequest request, String signature, String digits) {
        if (properties.isSignatureRequired() && !signatureValidator.isValid(request, signature)) {
            log.warn("Rejected IVR webhook call: missing or invalid Twilio signature");
            return ResponseEntity.status(403).contentType(MediaType.APPLICATION_XML).body(EMPTY_RESPONSE);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(ivrService.respond(digits));
    }
}
