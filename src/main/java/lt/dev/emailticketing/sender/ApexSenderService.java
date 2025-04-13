package lt.dev.emailticketing.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lt.dev.emailticketing.dto.EmailRequestDto;
import lt.dev.emailticketing.dto.MessageReplyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class ApexSenderService {

    private static final Logger logger = LoggerFactory.getLogger(ApexSenderService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Setter
    @Value("${apex.tickets.endpoint}")
    private String apexTicketsEndpoint;

    @Setter
    @Value("${apex.messages.endpoint}")
    private String apexMessagesEndpoint;

    @Value("${apex.api.key}")
    private String apexApiKey;

    public ApexSenderService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean sendToApex(Object dto) {
        String endpoint;
        if (dto instanceof EmailRequestDto) {
            endpoint = apexTicketsEndpoint;
        } else if (dto instanceof MessageReplyDto) {
            endpoint = apexMessagesEndpoint;
        } else {
            logger.error("Unsupported DTO type: {}", dto.getClass().getName());
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apexApiKey);
            String json = objectMapper.writeValueAsString(dto);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            logger.debug("Sending to APEX: {}", json);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully sent to APEX: {}", endpoint);
                return true;
            } else {
                logger.warn("APEX returned non-2xx status: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT && dto instanceof MessageReplyDto) {
                logger.info("Reply already processed by APEX: {}", ((MessageReplyDto) dto).getEmailId());
                return true;
            }
            logger.error("HTTP error sending to APEX: {}", e.getResponseBodyAsString(), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error sending to APEX: {}", e.getMessage(), e);
            return false;
        }
    }
}