package com.credchain.modules.notification.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Hands emails to the sender only after the transaction that asked for them commits (never for a rolled-back action). */
@Component
@RequiredArgsConstructor
class EmailDispatcher {

    private final EmailSender sender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(EmailMessage message) {
        sender.send(message);
    }
}
