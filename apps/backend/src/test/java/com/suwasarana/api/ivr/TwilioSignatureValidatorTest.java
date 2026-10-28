package com.suwasarana.api.ivr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioSignatureValidatorTest {

    private IvrProperties props;
    private TwilioSignatureValidator validator;

    @BeforeEach
    void setUp() {
        props = new IvrProperties();
        props.setAuthToken("12345");
        validator = new TwilioSignatureValidator(props);
    }

    /** Twilio's own documented example: token 12345, this URL and these parameters sign to RSOYDt4T1cUTdK1PDd93/VVr8B8= */
    private MockHttpServletRequest twilioExample() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/myapp.php");
        r.setScheme("https");
        r.setServerName("mycompany.com");
        r.setServerPort(443);
        r.setSecure(true);
        r.setQueryString("foo=1&bar=2");
        r.setParameter("foo", "1");
        r.setParameter("bar", "2");
        r.setParameter("CallSid", "CA1234567890ABCDE");
        r.setParameter("Caller", "+14158675309");
        r.setParameter("Digits", "1234");
        r.setParameter("From", "+14158675309");
        r.setParameter("To", "+18005551212");
        return r;
    }

    @Test
    @DisplayName("The signature in Twilio's documentation validates")
    void documentedExample() {
        assertTrue(validator.isValid(twilioExample(), "RSOYDt4T1cUTdK1PDd93/VVr8B8="));
    }

    @Test
    @DisplayName("Wrong, truncated, modified or missing signatures are refused")
    void invalidSignatures() {
        MockHttpServletRequest r = twilioExample();
        assertFalse(validator.isValid(r, "RSOYDt4T1cUTdK1PDd93/VVr8B8"));
        assertFalse(validator.isValid(r, "AAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        assertFalse(validator.isValid(r, ""));
        assertFalse(validator.isValid(r, null));
    }

    @Test
    @DisplayName("Changing any parameter or the URL invalidates the signature")
    void tamperedRequest() {
        MockHttpServletRequest r = twilioExample();
        r.setParameter("Digits", "3");
        assertFalse(validator.isValid(r, "RSOYDt4T1cUTdK1PDd93/VVr8B8="));

        MockHttpServletRequest other = twilioExample();
        other.setServerName("evil.example");
        assertFalse(validator.isValid(other, "RSOYDt4T1cUTdK1PDd93/VVr8B8="));
    }

    @Test
    @DisplayName("A different auth token does not validate; without any token nothing validates (fail closed)")
    void tokenHandling() {
        MockHttpServletRequest r = twilioExample();
        props.setAuthToken("other-token");
        assertFalse(validator.isValid(r, "RSOYDt4T1cUTdK1PDd93/VVr8B8="));
        props.setAuthToken("");
        assertFalse(validator.isValid(r, "RSOYDt4T1cUTdK1PDd93/VVr8B8="));
        assertFalse(validator.isValid(r, TwilioSignatureValidator.sign("12345", "anything")));
    }

    @Test
    @DisplayName("GET: the signature covers the URL with its query string and no parameters are appended")
    void getRequest() {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/ivr/webhook");
        r.setScheme("https");
        r.setServerName("api.example.lk");
        r.setServerPort(443);
        r.setQueryString("Digits=1");
        r.setParameter("Digits", "1");
        String sig = TwilioSignatureValidator.sign("12345", "https://api.example.lk/api/ivr/webhook?Digits=1");
        assertTrue(validator.isValid(r, sig));
        r.setParameter("Digits", "2");
        r.setQueryString("Digits=2");
        assertFalse(validator.isValid(r, sig));
    }

    @Test
    @DisplayName("Behind a proxy the configured public URL is what was signed")
    void publicUrl() {
        props.setPublicUrl("https://api.example.lk/api/ivr/webhook");
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/ivr/webhook");
        r.setScheme("http"); // what the application sees behind the load balancer
        r.setServerName("10.0.3.7");
        r.setServerPort(8080);
        r.setParameter("Digits", "2");
        r.setParameter("From", "+94771234567");
        String sig = TwilioSignatureValidator.sign("12345", "https://api.example.lk/api/ivr/webhookDigits2From+94771234567");
        assertTrue(validator.isValid(r, sig));
    }

    @Test
    @DisplayName("Twilio may sign the URL with or without the default port")
    void defaultPortVariants() {
        props.setPublicUrl("https://api.example.lk/api/ivr/webhook");
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/ivr/webhook");
        r.setParameter("Digits", "1");
        String withPort = TwilioSignatureValidator.sign("12345", "https://api.example.lk:443/api/ivr/webhookDigits1");
        assertTrue(validator.isValid(r, withPort));

        props.setPublicUrl("https://api.example.lk:443/api/ivr/webhook");
        String withoutPort = TwilioSignatureValidator.sign("12345", "https://api.example.lk/api/ivr/webhookDigits1");
        assertTrue(validator.isValid(r, withoutPort));
    }

    @Test
    @DisplayName("Default-port toggling helper")
    void togglePort() {
        assertEquals("https://h.lk:443/p?q=1", TwilioSignatureValidator.withToggledDefaultPort("https://h.lk/p?q=1"));
        assertEquals("https://h.lk/p", TwilioSignatureValidator.withToggledDefaultPort("https://h.lk:443/p"));
        assertEquals("http://h.lk:80/p", TwilioSignatureValidator.withToggledDefaultPort("http://h.lk/p"));
        assertNull(TwilioSignatureValidator.withToggledDefaultPort("https://h.lk:8443/p"));
        assertNull(TwilioSignatureValidator.withToggledDefaultPort("not a url"));
    }
}
