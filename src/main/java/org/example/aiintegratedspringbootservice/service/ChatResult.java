package org.example.aiintegratedspringbootservice.service;

/**
 * Outcome of a single chat orchestration.
 *
 * @param reply        the assistant's reply text
 * @param sessionId    the session id used to store history (echoed back to the
 *                     caller — may have been server-generated if absent on input)
 * @param personality  the personality applied (echoed back for clarity)
 */
public record ChatResult(String reply, String sessionId, String personality) {
}
