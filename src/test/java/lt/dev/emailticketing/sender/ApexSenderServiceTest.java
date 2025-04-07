package lt.dev.emailticketing.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.dev.emailticketing.dto.EmailRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApexSenderServiceTest {

    private ApexSenderService apexSenderService;
    private RestTemplate restTemplate;

    @BeforeEach
    @SuppressWarnings("HttpUrlsUsage")
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();

        apexSenderService = new ApexSenderService(restTemplate, objectMapper);
        apexSenderService.setApexApiKey("fake-api-key");
        apexSenderService.setApexTicketsEndpoint("http://fake-endpoint.com");
    }

    @Test
    void sendToApex_shouldReturnTrueOnSuccess() {
        EmailRequestDto dto = new EmailRequestDto(
                "123",
                "John",
                "john@example.com",
                "Subject",
                "Body",
                "2024-03-29T14:00:00Z",
                "345"
        );

        ResponseEntity<String> mockResponse = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        boolean result = apexSenderService.sendToApex(dto);

        assertTrue(result);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void sendToApex_shouldReturnFalseOnException() {
        EmailRequestDto dto = new EmailRequestDto(
                "123",
                "John",
                "john@example.com",
                "Subject",
                "Body",
                "2024-03-29T14:00:00Z",
                "345"
        );
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("APEX down"));

        boolean result = apexSenderService.sendToApex(dto);

        assertFalse(result, "Expected false when APEX sending fails");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }
}
