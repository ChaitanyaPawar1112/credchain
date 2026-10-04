package com.credchain.modules.student.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.student.api.dto.CreateStudentRequest;
import com.credchain.modules.student.api.dto.StudentImportResult;
import com.credchain.modules.student.api.dto.StudentImportResult.RowError;
import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.infrastructure.StudentRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bulk student import from CSV with per-row validation and an optional dry run. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCsvImportService {

    static final int MAX_ROWS = 5000;
    private static final int DB_CHECK_CHUNK = 1000;

    static final List<String> COLUMNS = List.of(
            "enrollmentNo", "fullName", "email", "dateOfBirth",
            "program", "department", "admissionYear", "graduationYear");
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "enrollmentNo", "fullName", "program", "admissionYear");

    /**
     * Accepts ISO (2004-03-18) and the Indian day-first formats Excel produces (18-03-2004, 18/03/2004).
     * STRICT rejects impossible dates such as 31-02-2004.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,

            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));

    private final StudentRepository studentRepository;
    private final InstitutionAccessGuard accessGuard;
    private final Validator validator;

    /** One parsed line: either a valid request or a list of problems. */
    private record ParsedRow(int row, String enrollmentNo, CreateStudentRequest request, List<String> errors) {
        boolean isValid() {
            return errors.isEmpty();
        }
    }

    // ---------- Template ----------

    public String template() {
        return String.join(",", COLUMNS) + "\n"
                + "2022CS101,Aarav Joshi,aarav@example.com,2004-03-18,B.Tech Computer Engineering,Computer Engineering,2022,2026\n";
    }

    // ---------- Import ----------

    @Transactional
    public StudentImportResult importCsv(UUID adminUserId, MultipartFile file, boolean dryRun) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        checkFile(file);

        List<ParsedRow> rows = parse(file);
        markDuplicatesInFile(rows);
        markExistingInInstitution(institutionId, rows);


        List<ParsedRow> validRows = rows.stream().filter(ParsedRow::isValid).toList();
        List<RowError> errors = rows.stream()
                .filter(r -> !r.isValid())
                .map(r -> new RowError(r.row(), r.enrollmentNo(), r.errors()))
                .toList();

        int imported = 0;
        if (!dryRun && !validRows.isEmpty()) {
            List<Student> students = validRows.stream().map(r -> toStudent(institutionId, r.request())).toList();
            try {
                studentRepository.saveAllAndFlush(students);   // batched INSERTs, one transaction
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.STUDENT_ALREADY_EXISTS,
                        "Some enrollment numbers were added by someone else during the import. Please retry.");
            }
            imported = students.size();
            log.info("CSV import: {} students added to institution {} ({} rows rejected)",
                    imported, institutionId, errors.size());
        }

        return new StudentImportResult(rows.size(), validRows.size(), imported, errors.size(), dryRun, errors);
    }

    // ---------- File-level checks ----------

    private static void checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "The uploaded file is empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "Only .csv files are accepted");

        }
    }

    // ---------- Parsing ----------

    private List<ParsedRow> parse(MultipartFile file) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()                 // first line = column names
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)   // "EnrollmentNo" == "enrollmentno"
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (Reader reader = bomSafeReader(file.getInputStream());
             CSVParser parser = format.parse(reader)) {

            checkHeaders(parser.getHeaderNames());

            List<ParsedRow> rows = new ArrayList<>();
            int rowNumber = 1;   // header is row 1
            for (CSVRecord record : parser) {
                rowNumber++;
                if (rows.size() >= MAX_ROWS) {
                    throw new BusinessException(ErrorCode.INVALID_FILE,
                            "Too many rows: the limit is " + MAX_ROWS + " students per file");
                }
                rows.add(parseRow(rowNumber, record));
            }
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_FILE, "The file has a header but no student rows");
            }

            return rows;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "Could not read the CSV file: " + e.getMessage());
        }
    }

    private static void checkHeaders(List<String> headers) {
        Set<String> present = new HashSet<>();
        headers.forEach(h -> present.add(h.toLowerCase(Locale.ROOT)));
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(c -> !present.contains(c.toLowerCase(Locale.ROOT)))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE,
                    "Missing required column(s): " + String.join(", ", missing)
                            + ". Download the template for the correct format.");
        }
    }

    private ParsedRow parseRow(int rowNumber, CSVRecord record) {
        List<String> errors = new ArrayList<>();

        String enrollmentNo = value(record, "enrollmentNo");
        Integer admissionYear = parseInt(value(record, "admissionYear"), "admissionYear", errors);
        Integer graduationYear = parseInt(value(record, "graduationYear"), "graduationYear", errors);
        LocalDate dateOfBirth = parseDate(value(record, "dateOfBirth"), errors);

        CreateStudentRequest request = new CreateStudentRequest(
                enrollmentNo,
                value(record, "fullName"),
                value(record, "email"),
                dateOfBirth,

                value(record, "program"),
                value(record, "department"),
                admissionYear,
                graduationYear);

        // Fields that already failed parsing shouldn't get a second "must not be null" message
        Set<String> alreadyFailed = new HashSet<>();
        errors.forEach(e -> alreadyFailed.add(e.substring(0, e.indexOf(':'))));

        // Same validation rules as the single "add student" API
        validator.validate(request).stream()
                .filter(v -> !alreadyFailed.contains(v.getPropertyPath().toString()))
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(StudentCsvImportService::describe)
                .forEach(errors::add);

        if (admissionYear != null && graduationYear != null && graduationYear < admissionYear) {
            errors.add("graduationYear: cannot be before admissionYear");
        }

        String normalized = enrollmentNo == null ? null : Student.normalizeEnrollmentNo(enrollmentNo);
        return new ParsedRow(rowNumber, normalized, request, errors);
    }

    // ---------- Duplicate checks ----------

    private static void markDuplicatesInFile(List<ParsedRow> rows) {
        Map<String, Integer> firstSeen = new HashMap<>();
        for (ParsedRow row : rows) {
            if (row.enrollmentNo() == null) {
                continue;
            }

            Integer first = firstSeen.putIfAbsent(row.enrollmentNo(), row.row());
            if (first != null) {
                row.errors().add("enrollmentNo: duplicate of row " + first + " in this file");
            }
        }
    }

    /** One query per 1000 enrollment numbers instead of one query per row. */
    private void markExistingInInstitution(UUID institutionId, List<ParsedRow> rows) {
        List<String> candidates = rows.stream()
                .filter(ParsedRow::isValid)
                .map(ParsedRow::enrollmentNo)
                .toList();

        Set<String> existing = new LinkedHashSet<>();
        for (int i = 0; i < candidates.size(); i += DB_CHECK_CHUNK) {
            List<String> chunk = candidates.subList(i, Math.min(i + DB_CHECK_CHUNK, candidates.size()));
            existing.addAll(studentRepository.findExistingEnrollmentNos(institutionId, chunk));
        }

        rows.stream()
                .filter(r -> r.isValid() && existing.contains(r.enrollmentNo()))
                .forEach(r -> r.errors().add("enrollmentNo: already exists in your institution"));
    }

    // ---------- helpers ----------

    private static Student toStudent(UUID institutionId, CreateStudentRequest r) {
        return Student.create(institutionId, r.enrollmentNo(), r.fullName(), r.email(), r.dateOfBirth(),
                r.program(), r.department(), r.admissionYear(), r.graduationYear());
    }


    /** Value of a column, or null if the column is missing / the cell is blank. */
    private static String value(CSVRecord record, String column) {
        if (!record.isSet(column)) {
            return null;
        }
        String v = record.get(column);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static Integer parseInt(String raw, String field, List<String> errors) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            errors.add(field + ": must be a number, got '" + raw + "'");
            return null;
        }
    }

    private static LocalDate parseDate(String raw, List<String> errors) {
        if (raw == null) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }

        errors.add("dateOfBirth: use YYYY-MM-DD or DD-MM-YYYY, got '" + raw + "'");
        return null;
    }

    private static String describe(ConstraintViolation<CreateStudentRequest> v) {
        return v.getPropertyPath() + ": " + v.getMessage();
    }

    /** Excel adds an invisible UTF-8 "byte order mark" at the start of CSV files; skip it. */
    private static Reader bomSafeReader(InputStream in) throws IOException {
        PushbackReader reader = new PushbackReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1);
        int first = reader.read();
        if (first != -1 && first != '\uFEFF') {
            reader.unread(first);
        }
        return reader;
    }
}