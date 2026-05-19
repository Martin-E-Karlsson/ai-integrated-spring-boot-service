package org.example.aiintegratedspringbootservice.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChatMemoryService}.
 *
 * Specifies behaviour, NOT implementation. The service stores per-session
 * chat history in a bounded FIFO structure (oldest evicted first) and is
 * safe under concurrent access.
 */
class ChatMemoryServiceTest {

    private static final int MAX_MESSAGES = 4;

    private ChatMemoryService newService() {
        return new ChatMemoryService(MAX_MESSAGES);
    }

    @Nested
    @DisplayName("history(sessionId)")
    class History {

        @Test
        @DisplayName("returns empty list for an unknown session")
        void emptyForUnknownSession() {
            ChatMemoryService service = newService();

            assertThat(service.history("unknown")).isEmpty();
        }

        @Test
        @DisplayName("returns messages in insertion order")
        void preservesInsertionOrder() {
            ChatMemoryService service = newService();
            service.append("s1", ChatMessage.user("first"));
            service.append("s1", ChatMessage.assistant("second"));
            service.append("s1", ChatMessage.user("third"));

            assertThat(service.history("s1"))
                    .extracting(ChatMessage::content)
                    .containsExactly("first", "second", "third");
        }

        @Test
        @DisplayName("returns an immutable snapshot — mutating it does not affect the store")
        void snapshotIsIndependent() {
            ChatMemoryService service = newService();
            service.append("s1", ChatMessage.user("first"));

            List<ChatMessage> snapshot = service.history("s1");

            assertThatThrownBy(() -> snapshot.add(ChatMessage.user("sneaky")))
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThat(service.history("s1")).hasSize(1);
        }

        @Test
        @DisplayName("isolates messages by sessionId")
        void sessionsAreIsolated() {
            ChatMemoryService service = newService();
            service.append("s1", ChatMessage.user("alpha"));
            service.append("s2", ChatMessage.user("beta"));

            assertThat(service.history("s1"))
                    .extracting(ChatMessage::content)
                    .containsExactly("alpha");
            assertThat(service.history("s2"))
                    .extracting(ChatMessage::content)
                    .containsExactly("beta");
        }
    }

    @Nested
    @DisplayName("append(sessionId, message)")
    class Append {

        @Test
        @DisplayName("evicts the oldest message when capacity is exceeded (FIFO)")
        void evictsOldestWhenFull() {
            ChatMemoryService service = newService();
            IntStream.rangeClosed(1, 6).forEach(i ->
                    service.append("s1", ChatMessage.user("m" + i)));

            assertThat(service.history("s1"))
                    .extracting(ChatMessage::content)
                    .containsExactly("m3", "m4", "m5", "m6");
        }

        @Test
        @DisplayName("rejects null sessionId")
        void rejectsNullSessionId() {
            ChatMemoryService service = newService();

            assertThatThrownBy(() -> service.append(null, ChatMessage.user("x")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null message")
        void rejectsNullMessage() {
            ChatMemoryService service = newService();

            assertThatThrownBy(() -> service.append("s1", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("clear(sessionId)")
    class Clear {

        @Test
        @DisplayName("removes all messages for a session, others unaffected")
        void clearsOnlyTargetSession() {
            ChatMemoryService service = newService();
            service.append("s1", ChatMessage.user("a"));
            service.append("s2", ChatMessage.user("b"));

            service.clear("s1");

            assertThat(service.history("s1")).isEmpty();
            assertThat(service.history("s2")).hasSize(1);
        }
    }
}
