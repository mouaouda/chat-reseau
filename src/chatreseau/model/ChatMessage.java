package chatreseau.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Représente un message texte ou une image échangé entre utilisateurs du réseau local.
 */
public class ChatMessage {
    public enum Type {
        PRIVATE,
        GENERAL,
        IMAGE_PRIVATE,
        IMAGE_GENERAL,
        SYSTEM
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final String senderId;
    private final String senderName;
    private final String targetId; // "ALL" pour le salon général ou l'ID du destinataire
    private final String content;
    private final byte[] imageData;
    private final String timestamp;
    private final boolean fromMe;
    private final Type type;

    public ChatMessage(String senderId, String senderName, String targetId, String content, boolean fromMe, Type type) {
        this(senderId, senderName, targetId, content, null, fromMe, type);
    }

    public ChatMessage(String senderId, String senderName, String targetId, String content, byte[] imageData, boolean fromMe, Type type) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.targetId = targetId;
        this.content = content != null ? content : "";
        this.imageData = imageData;
        this.timestamp = LocalTime.now().format(TIME_FORMATTER);
        this.fromMe = fromMe;
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getContent() {
        return content;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public boolean hasImage() {
        return imageData != null && imageData.length > 0;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public Type getType() {
        return type;
    }
}
