package lt.dev.emailticketing.service;

import lt.dev.emailticketing.dto.TicketByEmailResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TicketService {
    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    private final RestTemplate restTemplate;
    private final Map<String, Long> emailIdToTicketIdCache = new ConcurrentHashMap<>();

    @Value("${apex.tickets.endpoint}")
    private String apexTicketsEndpoint;

    @Value("${apex.api.key}")
    private String apexApiKey;

    public TicketService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Long getTicketIdByEmailId(String emailId) {
        return emailIdToTicketIdCache.computeIfAbsent(emailId, this::fetchTicketIdFromApex);
    }

    private Long fetchTicketIdFromApex(String emailId) {
        try {
            String url = UriComponentsBuilder.fromUriString(apexTicketsEndpoint)
                    .queryParam("emailId", emailId)
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apexApiKey);

            ResponseEntity<TicketByEmailResponseDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null
                    && response.getBody().getItems() != null
                    && !response.getBody().getItems().isEmpty()) {

                Long ticketId = response.getBody().getItems().getFirst().getTicketId();
                logger.debug("✅ Found ticket ID {} for email ID {}", ticketId, emailId);
                return ticketId;
            }

            logger.warn("⚠️ No ticket found for email ID {}", emailId);
            return null;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.debug("ℹ️ No ticket found for email ID {}", emailId);
                return null;
            }
            logger.error("❌ HTTP error fetching ticket: {} - {}", e.getStatusCode(), e.getStatusText());
            throw e;
        } catch (Exception e) {
            logger.error("❌ Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching ticket by email ID", e);
        }
    }

    @Scheduled(fixedRate = 3600000) // Refresh cache every hour
    public void refreshCache() {
        emailIdToTicketIdCache.clear();
        logger.info("🔄 Cleared ticket ID cache");
    }
}