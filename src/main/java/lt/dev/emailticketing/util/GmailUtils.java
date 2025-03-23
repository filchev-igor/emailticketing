package lt.dev.emailticketing.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

public class GmailUtils {

    private static final Logger logger = LoggerFactory.getLogger(GmailUtils.class);

    public static String decodeBase64(String encodedData) {
        if (encodedData == null) return "";
        try {
            String standardBase64 = encodedData.replace('-', '+').replace('_', '/');
            int padding = (4 - standardBase64.length() % 4) % 4;
            standardBase64 += "=".repeat(padding);
            return new String(Base64.getDecoder().decode(standardBase64));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to decode Base64 data: {}", encodedData, e);
            return "";
        }
    }

    public static String normalizeParagraphs(String text) {
        String[] lines = text.split("\r?\n");

        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.isEmpty()) {
                normalized.append("\n\n");
                continue;
            }

            normalized.append(line);

            if (i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                if (!nextLine.isEmpty()) {
                    normalized.append(" ");
                }
            }
        }
        return normalized.toString();
    }
}
