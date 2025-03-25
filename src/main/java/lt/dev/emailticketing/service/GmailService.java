package lt.dev.emailticketing.service;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import lt.dev.emailticketing.client.GmailClientService;
import lt.dev.emailticketing.dto.EmailRequestDto;
import lt.dev.emailticketing.dto.ProcessedEmailsResponseDto;
import lt.dev.emailticketing.dto.ProcessedEmailIdDto;
import lt.dev.emailticketing.internal.SenderInfo;

import lt.dev.emailticketing.parser.EmailParserService;
import lt.dev.emailticketing.sender.ApexSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class GmailService {
    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);

    private final EmailParserService emailParserService;
    private final ApexSenderService apexSenderService;
    private final GmailClientService gmailClientService;
    private final RestTemplate restTemplate = new RestTemplate();
    private Set<String> processedEmailIds;

    @Value("${apex.processed_emails.endpoint}")
    private String apexProcessedEmailsEndpoint;

    @Value("${apex.api.key}")
    private String apexApiKey;

    public GmailService(
            GmailClientService gmailClientService,
            EmailParserService emailParserService,
            ApexSenderService apexSenderService
    ) {
        this.gmailClientService = gmailClientService;
        this.emailParserService = emailParserService;
        this.apexSenderService = apexSenderService;
    }

    @PostConstruct
    public void init() throws Exception {
        loadProcessedEmailIdsWithRetries();
        gmailClientService.initClient();
    }

    @Scheduled(fixedRate = 60000)
    public void scanInbox() throws Exception {
        logger.info("Scanning Gmail inbox for new messages...");
        try {
            List<Message> messages = gmailClientService.fetchInboxMessages();

            if (messages != null) {
                Collections.reverse(messages);

                for (Message msg : messages) {
                    String emailId = msg.getId();
                    if (!processedEmailIds.contains(emailId)) {
                        logger.debug("Processing new email with ID: {}", emailId);
                        Message fullMsg = gmailClientService.fetchFullMessage(emailId);
                        String fromHeader = fullMsg.getPayload().getHeaders().stream()
                                .filter(h -> h.getName().equals("From"))
                                .findFirst().map(MessagePartHeader::getValue).orElse("Unknown");
                        SenderInfo senderInfo = emailParserService.extractSenderInfo(fromHeader);
                        String subject = fullMsg.getPayload().getHeaders().stream()
                                .filter(h -> h.getName().equals("Subject"))
                                .findFirst().map(MessagePartHeader::getValue).orElse("No Subject");
                        String body = emailParserService.extractBody(fullMsg);

                        EmailRequestDto dto = new EmailRequestDto(emailId, senderInfo.name(), senderInfo.email(), subject, body);

                        boolean success = apexSenderService.sendToApex(dto);

                        if (success) {
                            processedEmailIds.add(emailId);
                        }
                    } else {
                        logger.debug("Skipping already processed email with ID: {}", emailId);
                    }
                }
            } else {
                logger.info("No new messages found in inbox.");
            }
        } catch (TokenResponseException e) {
            if (e.getDetails() != null && "invalid_grant".equals(e.getDetails().getError())) {
                logger.warn("OAuth token expired or revoked. Reauthorizing...");

                gmailClientService.reauthorize();

                logger.info("Reauthorization successful. Will retry inbox scan on next scheduled run.");
            } else {
                throw e;
            }
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void refreshProcessedEmailIds() {
        logger.info("Refreshing processed email IDs to sync with external updates...");
        loadProcessedEmailIds();
        logger.info("Refreshed processed email IDs: {}", processedEmailIds);
    }

    private void loadProcessedEmailIdsWithRetries() throws InterruptedException {
        int retries = 3;
        processedEmailIds = new HashSet<>();
        while (retries > 0) {
            try {
                loadProcessedEmailIds();
                if (!processedEmailIds.isEmpty()) {
                    logger.debug("Successfully loaded processed email IDs: {}", processedEmailIds);
                    return;
                }
                logger.warn("Processed email IDs list is empty, retrying...");
            } catch (Exception e) {
                logger.error("Failed to load processed email IDs: {}", e.getMessage(), e);
            }
            retries--;
            if (retries > 0) {
                Thread.sleep(2000);
            }
        }
        logger.warn("Failed to load processed email IDs after 3 retries. Proceeding with empty set, which may cause reprocessing.");
    }

    private void loadProcessedEmailIds() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apexApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            logger.debug("Attempting to load processed email IDs from: {}", apexProcessedEmailsEndpoint);
            ResponseEntity<ProcessedEmailsResponseDto> response = restTemplate.exchange(
                    apexProcessedEmailsEndpoint,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().getItems() != null) {
                processedEmailIds.addAll(response.getBody().getItems().stream()
                        .map(ProcessedEmailIdDto::getEmailId)
                        .toList());
                logger.debug("Loaded {} processed email IDs from APEX.", processedEmailIds.size());
            } else {
                logger.warn("Received 200 OK but body or items list is null or empty from {}", apexProcessedEmailsEndpoint);
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP Error loading processed email IDs: {} - {}", e.getStatusCode(), e.getStatusText());
            e.getResponseBodyAsString();
            logger.error("Response Body: {}", e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to load processed email IDs: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected error loading processed email IDs", e);
        }
    }
}
