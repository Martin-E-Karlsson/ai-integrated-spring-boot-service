package org.example.aiintegratedspringbootservice.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-session, bounded, in-memory chat history.
 * <p>
 * Storage shape (per design decision D-009):
 * {@code ConcurrentHashMap<String, Deque<ChatMessage>>}, where:
 * <ul>
 *   <li>the outer map is keyed by {@code sessionId},</li>
 *   <li>the inner {@link ConcurrentLinkedDeque} holds messages in insertion
 *       order (oldest at the head, newest at the tail),</li>
 *   <li>the deque is bounded at {@code maxMessages}; when capacity is
 *       exceeded the head is evicted (FIFO).</li>
 * </ul>
 * <p>
 * Concurrency model: appends are atomic with eviction via a
 * {@code synchronized} block on the per-session deque. The outer map's
 * {@code computeIfAbsent} guarantees a single deque instance per session
 * even under concurrent first-access. The deque type is concurrency-aware
 * to make iteration cheaper for {@link #history(String)} snapshots.
 */
@Service
public class ChatMemoryService {

    private final Map<String, Deque<ChatMessage>> sessions = new ConcurrentHashMap<>();
    private final int maxMessages;

    public ChatMemoryService(@Value("${chat.memory.max-messages:20}") int maxMessages) {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be positive, was " + maxMessages);
        }
        this.maxMessages = maxMessages;
    }

    /**
     * Append a message to the given session's history, evicting the oldest
     * message if the session is at capacity.
     */
    public void append(String sessionId, ChatMessage message) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        Deque<ChatMessage> deque = sessions.computeIfAbsent(sessionId, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            deque.addLast(message);
            while (deque.size() > maxMessages) {
                deque.pollFirst();
            }
        }
    }

    /**
     * Return an immutable snapshot of the session's history in insertion order.
     * An unknown session yields an empty list.
     */
    public List<ChatMessage> history(String sessionId) {
        Deque<ChatMessage> deque = sessions.get(sessionId);
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            return Collections.unmodifiableList(new ArrayList<>(deque));
        }
    }

    /**
     * Forget everything we know about the given session. Idempotent.
     */
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
