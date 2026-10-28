package com.credchain.modules.notification.application;

import com.credchain.modules.certificate.domain.BatchAnchoredEvent;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateRevocationRequestedEvent;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.notification.config.MailProperties;
import com.credchain.modules.notification.domain.NotificationType;
import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.infrastructure.StudentRepository;
import com.credchain.modules.verification.domain.CertificateCheckedEvent;
import com.credchain.modules.verification.domain.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tells students and college admins about their certificates. Runs inside the transaction that published the
 * event, so a notification exists exactly when the change it describes was saved; emails go out after commit.
 */
@Component
@RequiredArgsConstructor
class CertificateNotifications {

    static final String STUDENT_PAGE = "/student/certificates";

    private final NotificationService notifications;
    private final CertificateRepository certificateRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final MailProperties mail;

    /** A batch is on the blockchain: each student hears about their certificate, the college about the batch. */
    @EventListener
    void on(BatchAnchoredEvent event) {
        String college = collegeName(event.institutionId());
        List<Certificate> certificates = certificateRepository.findAllByBatchIdOrderByCertificateNumber(event.batchId());
        for (Certificate certificate : certificates) {
            studentRepository.findById(certificate.getStudentId()).ifPresent(student -> {
                if (student.getUserId() != null) {
                    notifications.notify(student.getUserId(), NotificationType.CERTIFICATE_ISSUED,
                            "New certificate: " + certificate.getTitle(),
                            college + " issued your certificate " + certificate.getCertificateNumber()
                                    + ". It is recorded on the blockchain and anyone can now check it.",
                            STUDENT_PAGE);
                }
                notifications.email(student.getEmail(), "Your certificate from " + college + " is ready",
                        "Hello " + student.getFullName() + ",\n\n"
                                + college + " has issued your certificate \"" + certificate.getTitle() + "\" ("
                                + certificate.getCertificateNumber() + "). It is recorded on the blockchain.\n\n"
                                + (student.getUserId() != null
                                    ? "See and download it: " + mail.link(STUDENT_PAGE) + "\n"
                                    : "Ask your college for your claim code, then create your student account at "
                                        + mail.link("/register") + " to download it.\n"));
            });
        }
        notifications.notifyInstitutionAdmins(event.institutionId(), NotificationType.BATCH_ANCHORED,
                "Batch issued: " + event.title(),
                event.certificateCount() + " certificates are now recorded on the blockchain.",
                "/institution/batches/" + event.batchId(), false);
    }

    /** The college revoked a certificate: the student is told in the bell and by email (reason only, never the note). */
    @EventListener
    void on(CertificateRevocationRequestedEvent event) {
        Certificate certificate = certificateRepository.findById(event.certificateId()).orElse(null);
        if (certificate == null) {
            return;
        }
        Student student = studentRepository.findById(certificate.getStudentId()).orElse(null);
        if (student == null) {
            return;
        }
        String college = collegeName(certificate.getInstitutionId());
        String reason = certificate.getRevocationReason() == null ? ""
                : " (reason: " + readable(certificate.getRevocationReason().name()) + ")";
        if (student.getUserId() != null) {
            notifications.notify(student.getUserId(), NotificationType.CERTIFICATE_REVOKED,
                    "Certificate revoked: " + certificate.getTitle(),
                    college + " revoked your certificate " + certificate.getCertificateNumber() + reason
                            + ". Checks of it now answer \"revoked\".",
                    STUDENT_PAGE);
        }
        notifications.email(student.getEmail(), "Your certificate from " + college + " was revoked",
                "Hello " + student.getFullName() + ",\n\n"
                        + college + " revoked your certificate \"" + certificate.getTitle() + "\" ("
                        + certificate.getCertificateNumber() + ")" + reason + ".\n\n"
                        + "Please contact your college if you think this is a mistake.\n");
    }

    /**
     * A public check about a real certificate. Genuine: the student learns their certificate was checked.
     * Fake / edited copy: the college admins are warned. Both only once until read, so repeated checks do not pile up.
     */
    @EventListener
    void on(CertificateCheckedEvent event) {
        if (event.result() != VerificationStatus.VALID && event.result() != VerificationStatus.FAKE) {
            return;
        }
        Certificate certificate = certificateRepository.findById(event.certificateId()).orElse(null);
        if (certificate == null) {
            return;
        }
        if (event.result() == VerificationStatus.FAKE) {
            notifications.notifyInstitutionAdmins(certificate.getInstitutionId(), NotificationType.FAKE_COPY_CHECKED,
                    "A fake copy was checked",
                    "Someone checked a fake or edited copy of certificate " + certificate.getCertificateNumber()
                            + " (" + certificate.getStudentName() + "). The verification log shows every attempt.",
                    "/institution/verifications", true);
            return;
        }
        studentRepository.findById(certificate.getStudentId())
                .filter(student -> student.getUserId() != null)
                .ifPresent(student -> notifications.notifyOnce(student.getUserId(), NotificationType.CERTIFICATE_CHECKED,
                        "Your certificate was checked",
                        "Someone checked your certificate " + certificate.getCertificateNumber()
                                + " and was told it is genuine.",
                        STUDENT_PAGE + "?certificate=" + certificate.getCertificateNumber()));
    }

    private String collegeName(UUID institutionId) {
        return institutionRepository.findById(institutionId).map(Institution::getName).orElse("Your college");
    }

    /** "ISSUED_IN_ERROR" -> "issued in error". */
    private static String readable(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
