package lt.dev.emailticketing.parser;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import lt.dev.emailticketing.internal.SenderInfo;
import org.springframework.stereotype.Service;

import java.util.List;

import static lt.dev.emailticketing.util.GmailUtils.*;

@Service
public class EmailParserService {

    public SenderInfo extractSenderInfo(String fromHeader) {
        String name = "Unknown";
        String email = "unknown@unknown.com";

        if (fromHeader != null && !fromHeader.isEmpty()) {
            if (fromHeader.contains("<")) {
                name = fromHeader.substring(0, fromHeader.indexOf("<")).trim();
                email = fromHeader.substring(fromHeader.indexOf("<") + 1, fromHeader.indexOf(">")).trim();
            } else {
                email = fromHeader.trim();
                name = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
            }
        }

        return new SenderInfo(name, email);
    }

    public String extractBody(Message message) {
        StringBuilder body = new StringBuilder();

        if (message.getPayload() != null) {
            if (message.getPayload().getBody() != null && message.getPayload().getBody().getData() != null) {
                body.append(decodeBase64(message.getPayload().getBody().getData()));
            }

            List<MessagePart> parts = message.getPayload().getParts();
            if (parts != null) {
                for (MessagePart part : parts) {
                    if ("text/plain".equals(part.getMimeType())
                            && part.getBody() != null
                            && part.getBody().getData() != null) {
                        body.append(decodeBase64(part.getBody().getData()));
                    }
                }
            }
        }

        return normalizeParagraphs(body.toString());
    }
}
