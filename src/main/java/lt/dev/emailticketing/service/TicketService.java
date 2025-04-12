package lt.dev.emailticketing.service;

import lt.dev.emailticketing.dto.TicketByThreadDto;
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
    private final Map<String, Long> threadIdToTicketIdCache = new ConcurrentHashMap<>();

    @Value("${apex.tickets.endpoint}")
    private String apexTicketsEndpoint;

    @Value("${apex.api.key}")
    private String apexApiKey;

    public TicketService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Long getTicketIdByThreadId(String threadId) {
        return threadIdToTicketIdCache.computeIfAbsent(threadId, this::fetchTicketIdFromApex);
    }

    private Long fetchTicketIdFromApex(String threadId) {
        try {
            String url = UriComponentsBuilder.fromUriString(apexTicketsEndpoint)
                    .queryParam("email_thread_id", threadId)
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apexApiKey);

            logger.debug("Attempting to call APEX API with URL: {}", url);

            ResponseEntity<TicketByThreadDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            logger.debug("APEX API response: {}", response);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Long ticketId = response.getBody().getTicketId();
                if (ticketId != null) {
                    logger.debug("✅ Found ticket ID {} for thread ID {}", ticketId, threadId);

                    return ticketId;
                }
            }

            logger.warn("⚠️ No ticket found for thread ID {}", threadId);
            return null;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.debug("ℹ️ No ticket found for thread ID {}", threadId);
                return null;
            }
            logger.error("❌ HTTP error fetching ticket: {} - {}", e.getStatusCode(), e.getStatusText());
            throw e;
        } catch (Exception e) {
            logger.error("❌ Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching ticket by thread ID", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void refreshCache() {
        threadIdToTicketIdCache.clear();
        logger.info("🔄 Cleared ticket ID cache");
    }
}