package smartcampus.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import smartcampus.dto.ResumeAchievementResponse;
import smartcampus.dto.ResumeCertificationResponse;
import smartcampus.dto.ResumeEducationResponse;
import smartcampus.dto.ResumeExperienceResponse;
import smartcampus.dto.ResumeProjectResponse;
import smartcampus.dto.ResumeResponse;
import smartcampus.dto.ResumeSkillResponse;
import smartcampus.entity.EmploymentType;
import smartcampus.entity.GradeScale;
import smartcampus.entity.ResumeTemplate;
import smartcampus.entity.SkillCategory;
import smartcampus.entity.SkillProficiency;

/**
 * Renders a {@link ResumeResponse} to a PDF byte stream using OpenPDF ({@code
 * com.lowagie.text} / {@code com.lowagie.text.pdf}, group {@code com.github.librepdf},
 * artifact {@code openpdf}, version {@code 2.4.0}).
 *
 * <p>This service is the single code path behind the student's download, the student's
 * in-page preview, and the admin's download from the applicant list (clarification G9)
 * so that the exact bytes attachable to a placement application (§35) are always the
 * ones produced here — never a client-side export.
 *
 * <p>It receives only the fully-assembled {@link ResumeResponse} DTO and touches no
 * repository and no entity: {@code spring.jpa.open-in-view=false} means anything lazy
 * would explode outside the caller's transaction, so all data this class needs must
 * already be sitting in the DTO handed to {@link #render(ResumeResponse)}.
 *
 * <p><b>Known constraint — base-14 fonts.</b> This renders with the built-in PDF base-14
 * fonts (Times-Roman / Helvetica) via {@link FontFactory}, which are WinAnsi-encoded
 * (Latin-1). A student name or free-text field containing characters outside Latin-1
 * (e.g. Devanagari, CJK, Cyrillic beyond a few accented forms) will not render correctly
 * with these fonts. This is a deliberate scope decision for this phase, not an
 * oversight: fixing it requires bundling an embeddable Unicode font file, which is
 * explicitly out of scope here. Nothing in this class attempts to strip or transliterate
 * such characters into mojibake; the base-14 fonts simply cannot draw them.
 */
@Service
public class ResumePdfService {

    private static final DateTimeFormatter MONTH_YEAR =
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private static final Color MODERN_ACCENT = new Color(0x1E, 0x50, 0x8C);

    /**
     * Renders the given resume to PDF bytes. Never returns {@code null} or a
     * zero-length array; a rendering fault is surfaced as an {@link IllegalStateException}
     * rather than a silently empty document.
     */
    public byte[] render(ResumeResponse resume) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = newDocument(resume.template());
        try {
            PdfWriter.getInstance(document, baos);
            document.open();
            switch (resume.template()) {
                case CLASSIC -> renderClassic(document, resume);
                case MODERN -> renderModern(document, resume);
                case COMPACT -> renderCompact(document, resume);
                default ->
                    throw new IllegalStateException(
                        "Unsupported resume template: " + resume.template());
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render resume PDF", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        byte[] bytes = baos.toByteArray();
        if (bytes.length == 0) {
            // Defensive: OpenPDF should never produce this, but a §69 empty "PDF" must
            // never leave this method as if it were a real document.
            throw new IllegalStateException("Resume PDF rendering produced no bytes");
        }
        return bytes;
    }

    /** Builds the download file name: {@code resume-<slug-of-title>-<id>.pdf}. */
    public String fileName(ResumeResponse resume) {
        String slug = slugify(resume.title());
        String raw = "resume-" + slug + "-" + resume.id() + ".pdf";
        // Belt-and-braces: this reaches an HTTP Content-Disposition header, so it must
        // never carry a quote, newline, path separator or non-ASCII byte.
        return raw.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    // ------------------------------------------------------------------------------
    // Document setup
    // ------------------------------------------------------------------------------

    private Document newDocument(ResumeTemplate template) {
        float margin =
            switch (template) {
                case COMPACT -> 32f;
                default -> 42f;
            };
        return new Document(PageSize.A4, margin, margin, margin, margin);
    }

    // ------------------------------------------------------------------------------
    // CLASSIC — serif, single column, centred header, ruled section headings
    // ------------------------------------------------------------------------------

    private void renderClassic(Document document, ResumeResponse resume) throws DocumentException {
        Font nameFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 22f, Font.NORMAL, Color.BLACK);
        Font contactFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10.5f, Font.NORMAL, new Color(0x40, 0x40, 0x40));
        Font headingFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 12.5f, Font.NORMAL, Color.BLACK);
        Font bodyFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10.5f, Font.NORMAL, Color.BLACK);
        Font subFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10f, Font.ITALIC, new Color(0x30, 0x30, 0x30));
        Font strongFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 10.5f, Font.NORMAL, Color.BLACK);

        Paragraph name = new Paragraph(nz(resume.fullName()), nameFont);
        name.setAlignment(Element.ALIGN_CENTER);
        name.setSpacingAfter(4f);
        document.add(name);

        String contactLine = contactLine(resume);
        if (!contactLine.isEmpty()) {
            Paragraph contact = new Paragraph(contactLine, contactFont);
            contact.setAlignment(Element.ALIGN_CENTER);
            contact.setSpacingAfter(14f);
            document.add(contact);
        } else {
            document.add(spacer(10f));
        }

        boolean first = true;
        first = classicSummary(document, resume, headingFont, bodyFont, first);
        first = classicEducation(document, resume, headingFont, bodyFont, strongFont, first);
        first = classicExperience(document, resume, headingFont, bodyFont, subFont, strongFont, first);
        first = classicProjects(document, resume, headingFont, bodyFont, subFont, strongFont, first);
        first = classicCertifications(document, resume, headingFont, bodyFont, strongFont, first);
        first = classicSkills(document, resume, headingFont, bodyFont, strongFont, first);
        classicAchievements(document, resume, headingFont, bodyFont, subFont, strongFont, first);
    }

    private void classicHeading(Document document, String text, Font headingFont, boolean first)
        throws DocumentException {
        Paragraph heading = new Paragraph(text.toUpperCase(Locale.ENGLISH), headingFont);
        heading.setSpacingBefore(first ? 0f : 12f);
        heading.setSpacingAfter(2f);
        document.add(heading);
        LineSeparator rule = new LineSeparator(0.75f, 100f, new Color(0x80, 0x80, 0x80), Element.ALIGN_LEFT, -2f);
        Paragraph ruleParagraph = new Paragraph();
        ruleParagraph.add(new Chunk(rule));
        ruleParagraph.setSpacingAfter(6f);
        document.add(ruleParagraph);
    }

    private boolean classicSummary(
        Document document, ResumeResponse resume, Font headingFont, Font bodyFont, boolean first)
        throws DocumentException {
        if (isBlank(resume.summary())) {
            return first;
        }
        classicHeading(document, "Summary", headingFont, first);
        addParagraphLines(document, resume.summary(), bodyFont, 2f);
        return false;
    }

    private boolean classicEducation(
        Document document,
        ResumeResponse resume,
        Font headingFont,
        Font bodyFont,
        Font strongFont,
        boolean first)
        throws DocumentException {
        if (resume.educations().isEmpty()) {
            return first;
        }
        classicHeading(document, "Education", headingFont, first);
        for (ResumeEducationResponse edu : resume.educations()) {
            Paragraph line = new Paragraph();
            line.add(new Chunk(nz(edu.institution()), strongFont));
            String degreeLine = degreeAndField(edu.degree(), edu.fieldOfStudy());
            if (!degreeLine.isEmpty()) {
                line.add(new Chunk(" — " + degreeLine, bodyFont));
            }
            line.setSpacingAfter(1f);
            document.add(line);

            String years = yearRange(edu.startYear(), edu.endYear());
            String grade = gradeText(edu.gradeValue(), edu.gradeScale());
            String meta = joinNonBlank(" · ", years, grade);
            if (!meta.isEmpty()) {
                Paragraph metaLine = new Paragraph(meta, bodyFont);
                metaLine.setSpacingAfter(6f);
                document.add(metaLine);
            } else {
                document.add(spacer(6f));
            }
        }
        return false;
    }

    private boolean classicExperience(
        Document document,
        ResumeResponse resume,
        Font headingFont,
        Font bodyFont,
        Font subFont,
        Font strongFont,
        boolean first)
        throws DocumentException {
        if (resume.experiences().isEmpty()) {
            return first;
        }
        classicHeading(document, "Experience", headingFont, first);
        for (ResumeExperienceResponse exp : resume.experiences()) {
            Paragraph titleLine = new Paragraph();
            titleLine.add(new Chunk(nz(exp.roleTitle()) + ", " + nz(exp.companyName()), strongFont));
            titleLine.setSpacingAfter(1f);
            document.add(titleLine);

            String dateRange = dateRange(exp.startDate(), exp.endDate(), exp.currentPosition());
            String meta = joinNonBlank(
                " · ", dateRange, exp.location(), employmentLabel(exp.employmentType()));
            if (!meta.isEmpty()) {
                document.add(new Paragraph(meta, subFont));
            }
            addParagraphLines(document, exp.description(), bodyFont, 6f);
        }
        return false;
    }

    private boolean classicProjects(
        Document document,
        ResumeResponse resume,
        Font headingFont,
        Font bodyFont,
        Font subFont,
        Font strongFont,
        boolean first)
        throws DocumentException {
        if (resume.projects().isEmpty()) {
            return first;
        }
        classicHeading(document, "Projects", headingFont, first);
        for (ResumeProjectResponse proj : resume.projects()) {
            Paragraph titleLine = new Paragraph(nz(proj.name()), strongFont);
            titleLine.setSpacingAfter(1f);
            document.add(titleLine);

            String dateRange = dateRange(proj.startDate(), proj.endDate(), false);
            String meta = joinNonBlank(" · ", dateRange, proj.techStack(), proj.projectUrl(), proj.repositoryUrl());
            if (!meta.isEmpty()) {
                document.add(new Paragraph(meta, subFont));
            }
            addParagraphLines(document, proj.description(), bodyFont, 6f);
        }
        return false;
    }

    private boolean classicCertifications(
        Document document, ResumeResponse resume, Font headingFont, Font bodyFont, Font strongFont, boolean first)
        throws DocumentException {
        if (resume.certifications().isEmpty()) {
            return first;
        }
        classicHeading(document, "Certifications", headingFont, first);
        for (ResumeCertificationResponse cert : resume.certifications()) {
            Paragraph line = new Paragraph();
            line.add(new Chunk(nz(cert.name()), strongFont));
            String issuer = joinNonBlank(
                ", ", cert.issuer(), certDateLabel(cert.issueDate(), cert.expiryDate()));
            if (!issuer.isEmpty()) {
                line.add(new Chunk(" — " + issuer, bodyFont));
            }
            line.setSpacingAfter(6f);
            document.add(line);
        }
        return false;
    }

    private boolean classicSkills(
        Document document, ResumeResponse resume, Font headingFont, Font bodyFont, Font strongFont, boolean first)
        throws DocumentException {
        if (resume.skills().isEmpty()) {
            return first;
        }
        classicHeading(document, "Skills", headingFont, first);
        for (Map.Entry<SkillCategory, String> group : groupSkills(resume.skills()).entrySet()) {
            Paragraph line = new Paragraph();
            line.add(new Chunk(skillCategoryLabel(group.getKey()) + ": ", strongFont));
            line.add(new Chunk(group.getValue(), bodyFont));
            line.setSpacingAfter(3f);
            document.add(line);
        }
        document.add(spacer(3f));
        return false;
    }

    private void classicAchievements(
        Document document,
        ResumeResponse resume,
        Font headingFont,
        Font bodyFont,
        Font subFont,
        Font strongFont,
        boolean first)
        throws DocumentException {
        if (resume.achievements().isEmpty()) {
            return;
        }
        classicHeading(document, "Achievements", headingFont, first);
        for (ResumeAchievementResponse ach : resume.achievements()) {
            Paragraph line = new Paragraph();
            line.add(new Chunk(nz(ach.title()), strongFont));
            String meta = joinNonBlank(" · ", ach.issuer(), formatDateOrNull(ach.achievedOn()));
            if (!meta.isEmpty()) {
                line.add(new Chunk("  (" + meta + ")", subFont));
            }
            line.setSpacingAfter(1f);
            document.add(line);
            addParagraphLines(document, ach.description(), bodyFont, 6f);
        }
    }

    // ------------------------------------------------------------------------------
    // MODERN — sans, left header with accent rule, accent-coloured headings, 2-col skills
    // ------------------------------------------------------------------------------

    private void renderModern(Document document, ResumeResponse resume) throws DocumentException {
        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24f, Font.NORMAL, Color.BLACK);
        Font contactFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, Font.NORMAL, new Color(0x45, 0x45, 0x45));
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, Font.NORMAL, MODERN_ACCENT);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, Font.NORMAL, Color.BLACK);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, Font.NORMAL, new Color(0x50, 0x50, 0x50));
        Font strongFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, Font.NORMAL, Color.BLACK);

        Paragraph name = new Paragraph(nz(resume.fullName()), nameFont);
        name.setAlignment(Element.ALIGN_LEFT);
        name.setSpacingAfter(3f);
        document.add(name);

        LineSeparator accentRule = new LineSeparator(2f, 100f, MODERN_ACCENT, Element.ALIGN_LEFT, -2f);
        Paragraph ruleParagraph = new Paragraph();
        ruleParagraph.add(new Chunk(accentRule));
        ruleParagraph.setSpacingAfter(5f);
        document.add(ruleParagraph);

        String contactLine = contactLine(resume);
        if (!contactLine.isEmpty()) {
            Paragraph contact = new Paragraph(contactLine, contactFont);
            contact.setSpacingAfter(14f);
            document.add(contact);
        } else {
            document.add(spacer(10f));
        }

        boolean first = true;
        if (!isBlank(resume.summary())) {
            modernHeading(document, "Summary", headingFont, first);
            addParagraphLines(document, resume.summary(), bodyFont, 2f);
            first = false;
        }
        if (!resume.educations().isEmpty()) {
            modernHeading(document, "Education", headingFont, first);
            for (ResumeEducationResponse edu : resume.educations()) {
                Paragraph line = new Paragraph();
                line.add(new Chunk(nz(edu.institution()), strongFont));
                String degreeLine = degreeAndField(edu.degree(), edu.fieldOfStudy());
                if (!degreeLine.isEmpty()) {
                    line.add(new Chunk("  " + degreeLine, bodyFont));
                }
                line.setSpacingAfter(1f);
                document.add(line);
                String meta = joinNonBlank(
                    " · ", yearRange(edu.startYear(), edu.endYear()), gradeText(edu.gradeValue(), edu.gradeScale()));
                Paragraph metaLine = new Paragraph(meta.isEmpty() ? " " : meta, subFont);
                metaLine.setSpacingAfter(6f);
                document.add(metaLine);
            }
            first = false;
        }
        if (!resume.experiences().isEmpty()) {
            modernHeading(document, "Experience", headingFont, first);
            for (ResumeExperienceResponse exp : resume.experiences()) {
                Paragraph titleLine = new Paragraph();
                titleLine.add(new Chunk(nz(exp.roleTitle()), strongFont));
                titleLine.add(new Chunk("  " + nz(exp.companyName()), bodyFont));
                titleLine.setSpacingAfter(1f);
                document.add(titleLine);
                String meta = joinNonBlank(
                    " · ",
                    dateRange(exp.startDate(), exp.endDate(), exp.currentPosition()),
                    exp.location(),
                    employmentLabel(exp.employmentType()));
                if (!meta.isEmpty()) {
                    document.add(new Paragraph(meta, subFont));
                }
                addParagraphLines(document, exp.description(), bodyFont, 6f);
            }
            first = false;
        }
        if (!resume.projects().isEmpty()) {
            modernHeading(document, "Projects", headingFont, first);
            for (ResumeProjectResponse proj : resume.projects()) {
                Paragraph titleLine = new Paragraph(nz(proj.name()), strongFont);
                titleLine.setSpacingAfter(1f);
                document.add(titleLine);
                String meta = joinNonBlank(
                    " · ",
                    dateRange(proj.startDate(), proj.endDate(), false),
                    proj.techStack(),
                    proj.projectUrl(),
                    proj.repositoryUrl());
                if (!meta.isEmpty()) {
                    document.add(new Paragraph(meta, subFont));
                }
                addParagraphLines(document, proj.description(), bodyFont, 6f);
            }
            first = false;
        }
        if (!resume.certifications().isEmpty()) {
            modernHeading(document, "Certifications", headingFont, first);
            for (ResumeCertificationResponse cert : resume.certifications()) {
                Paragraph line = new Paragraph();
                line.add(new Chunk(nz(cert.name()), strongFont));
                String issuer = joinNonBlank(", ", cert.issuer(), certDateLabel(cert.issueDate(), cert.expiryDate()));
                if (!issuer.isEmpty()) {
                    line.add(new Chunk("  " + issuer, bodyFont));
                }
                line.setSpacingAfter(6f);
                document.add(line);
            }
            first = false;
        }
        if (!resume.skills().isEmpty()) {
            modernHeading(document, "Skills", headingFont, first);
            Map<SkillCategory, String> groups = groupSkills(resume.skills());
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100f);
            table.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            int i = 0;
            for (Map.Entry<SkillCategory, String> group : groups.entrySet()) {
                Phrase phrase = new Phrase();
                phrase.add(new Chunk(skillCategoryLabel(group.getKey()) + "\n", strongFont));
                phrase.add(new Chunk(group.getValue(), bodyFont));
                PdfPCell cell = new PdfPCell(phrase);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPaddingBottom(6f);
                cell.setPaddingRight(10f);
                table.addCell(cell);
                i++;
            }
            if (i % 2 == 1) {
                PdfPCell blank = new PdfPCell(new Phrase(""));
                blank.setBorder(Rectangle.NO_BORDER);
                table.addCell(blank);
            }
            document.add(table);
            first = false;
        }
        if (!resume.achievements().isEmpty()) {
            modernHeading(document, "Achievements", headingFont, first);
            for (ResumeAchievementResponse ach : resume.achievements()) {
                Paragraph line = new Paragraph();
                line.add(new Chunk(nz(ach.title()), strongFont));
                String meta = joinNonBlank(" · ", ach.issuer(), formatDateOrNull(ach.achievedOn()));
                if (!meta.isEmpty()) {
                    line.add(new Chunk("  (" + meta + ")", subFont));
                }
                line.setSpacingAfter(1f);
                document.add(line);
                addParagraphLines(document, ach.description(), bodyFont, 6f);
            }
        }
    }

    private void modernHeading(Document document, String text, Font headingFont, boolean first)
        throws DocumentException {
        Paragraph heading = new Paragraph(text.toUpperCase(Locale.ENGLISH), headingFont);
        heading.setSpacingBefore(first ? 0f : 12f);
        heading.setSpacingAfter(5f);
        document.add(heading);
    }

    // ------------------------------------------------------------------------------
    // COMPACT — sans, tight leading, inline headings, one-line comma-joined skills
    // ------------------------------------------------------------------------------

    private void renderCompact(Document document, ResumeResponse resume) throws DocumentException {
        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15f, Font.NORMAL, Color.BLACK);
        Font contactFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, new Color(0x45, 0x45, 0x45));
        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Font.NORMAL, new Color(0x20, 0x20, 0x20));
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, Color.BLACK);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f, Font.NORMAL, new Color(0x55, 0x55, 0x55));
        Font strongFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Font.NORMAL, Color.BLACK);

        Paragraph name = new Paragraph(nz(resume.fullName()), nameFont);
        name.setSpacingAfter(2f);
        document.add(name);

        String contactLine = contactLine(resume);
        if (!contactLine.isEmpty()) {
            Paragraph contact = new Paragraph(contactLine, contactFont);
            contact.setSpacingAfter(8f);
            document.add(contact);
        } else {
            document.add(spacer(6f));
        }

        boolean first = true;
        if (!isBlank(resume.summary())) {
            compactHeading(document, "SUMMARY", headingFont, first);
            addParagraphLines(document, resume.summary(), bodyFont, 1f);
            first = false;
        }
        if (!resume.educations().isEmpty()) {
            compactHeading(document, "EDUCATION", headingFont, first);
            for (ResumeEducationResponse edu : resume.educations()) {
                Paragraph line = new Paragraph();
                line.add(new Chunk(nz(edu.institution()), strongFont));
                String degreeLine = degreeAndField(edu.degree(), edu.fieldOfStudy());
                String meta = joinNonBlank(
                    ", ", degreeLine, yearRange(edu.startYear(), edu.endYear()), gradeText(edu.gradeValue(), edu.gradeScale()));
                if (!meta.isEmpty()) {
                    line.add(new Chunk(" — " + meta, bodyFont));
                }
                line.setSpacingAfter(2f);
                document.add(line);
            }
            first = false;
        }
        if (!resume.experiences().isEmpty()) {
            compactHeading(document, "EXPERIENCE", headingFont, first);
            for (ResumeExperienceResponse exp : resume.experiences()) {
                Paragraph titleLine = new Paragraph();
                titleLine.add(new Chunk(nz(exp.roleTitle()) + ", " + nz(exp.companyName()), strongFont));
                String meta = joinNonBlank(
                    ", ",
                    dateRange(exp.startDate(), exp.endDate(), exp.currentPosition()),
                    exp.location(),
                    employmentLabel(exp.employmentType()));
                if (!meta.isEmpty()) {
                    titleLine.add(new Chunk(" (" + meta + ")", subFont));
                }
                titleLine.setSpacingAfter(1f);
                document.add(titleLine);
                addParagraphLines(document, exp.description(), bodyFont, 3f);
            }
            first = false;
        }
        if (!resume.projects().isEmpty()) {
            compactHeading(document, "PROJECTS", headingFont, first);
            for (ResumeProjectResponse proj : resume.projects()) {
                Paragraph titleLine = new Paragraph();
                titleLine.add(new Chunk(nz(proj.name()), strongFont));
                String meta = joinNonBlank(
                    ", ", dateRange(proj.startDate(), proj.endDate(), false), proj.techStack());
                if (!meta.isEmpty()) {
                    titleLine.add(new Chunk(" (" + meta + ")", subFont));
                }
                titleLine.setSpacingAfter(1f);
                document.add(titleLine);
                addParagraphLines(document, proj.description(), bodyFont, 3f);
            }
            first = false;
        }
        if (!resume.certifications().isEmpty()) {
            compactHeading(document, "CERTIFICATIONS", headingFont, first);
            for (ResumeCertificationResponse cert : resume.certifications()) {
                Paragraph line = new Paragraph();
                line.add(new Chunk(nz(cert.name()), strongFont));
                String issuer = joinNonBlank(", ", cert.issuer(), certDateLabel(cert.issueDate(), cert.expiryDate()));
                if (!issuer.isEmpty()) {
                    line.add(new Chunk(" — " + issuer, bodyFont));
                }
                line.setSpacingAfter(2f);
                document.add(line);
            }
            first = false;
        }
        if (!resume.skills().isEmpty()) {
            compactHeading(document, "SKILLS", headingFont, first);
            String allSkills = String.join(", ", flatSkillList(resume.skills()));
            Paragraph line = new Paragraph(allSkills, bodyFont);
            line.setSpacingAfter(4f);
            document.add(line);
            first = false;
        }
        if (!resume.achievements().isEmpty()) {
            compactHeading(document, "ACHIEVEMENTS", headingFont, first);
            for (ResumeAchievementResponse ach : resume.achievements()) {
                Paragraph line = new Paragraph();
                line.add(new Chunk(nz(ach.title()), strongFont));
                String meta = joinNonBlank(", ", ach.issuer(), formatDateOrNull(ach.achievedOn()));
                if (!meta.isEmpty()) {
                    line.add(new Chunk(" (" + meta + ")", subFont));
                }
                line.setSpacingAfter(2f);
                document.add(line);
                addParagraphLines(document, ach.description(), bodyFont, 3f);
            }
        }
    }

    private void compactHeading(Document document, String text, Font headingFont, boolean first)
        throws DocumentException {
        Paragraph heading = new Paragraph(text, headingFont);
        heading.setSpacingBefore(first ? 0f : 6f);
        heading.setSpacingAfter(2f);
        document.add(heading);
    }

    // ------------------------------------------------------------------------------
    // Shared formatting helpers
    // ------------------------------------------------------------------------------

    private String contactLine(ResumeResponse resume) {
        return joinNonBlank(
            " · ",
            resume.email(),
            resume.phone(),
            resume.location(),
            resume.linkedinUrl(),
            resume.githubUrl(),
            resume.portfolioUrl());
    }

    private void addParagraphLines(Document document, String text, Font font, float trailingSpacing)
        throws DocumentException {
        if (isBlank(text)) {
            return;
        }
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            Paragraph paragraph = new Paragraph(line, font);
            paragraph.setSpacingAfter(i == lines.length - 1 ? trailingSpacing : 1f);
            document.add(paragraph);
        }
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    private String degreeAndField(String degree, String fieldOfStudy) {
        return joinNonBlank(", ", degree, fieldOfStudy);
    }

    private String yearRange(Integer startYear, Integer endYear) {
        if (startYear == null && endYear == null) {
            return "";
        }
        String start = startYear == null ? "" : String.valueOf(startYear);
        String end = endYear == null ? "" : String.valueOf(endYear);
        if (start.isEmpty() && end.isEmpty()) {
            return "";
        }
        return start + " – " + end;
    }

    private String gradeText(BigDecimal gradeValue, GradeScale gradeScale) {
        if (gradeValue == null) {
            return "";
        }
        BigDecimal scaled = gradeValue.setScale(2, java.math.RoundingMode.HALF_UP);
        if (gradeScale == GradeScale.PERCENTAGE) {
            return scaled + "%";
        }
        // CGPA (or, defensively, an unexpected non-null scale) prints with its label.
        return scaled + " " + (gradeScale == null ? "" : gradeScale.name());
    }

    private String dateRange(LocalDate start, LocalDate end, boolean currentPosition) {
        if (start == null) {
            return "";
        }
        String startText = start.format(MONTH_YEAR);
        String endText = currentPosition ? "Present" : (end == null ? "" : end.format(MONTH_YEAR));
        if (endText.isEmpty()) {
            return startText;
        }
        return startText + " – " + endText;
    }

    private String formatDateOrNull(LocalDate date) {
        return date == null ? "" : date.format(MONTH_YEAR);
    }

    private String certDateLabel(LocalDate issueDate, LocalDate expiryDate) {
        if (issueDate == null && expiryDate == null) {
            return "";
        }
        String issued = issueDate == null ? "" : "Issued " + issueDate.format(MONTH_YEAR);
        String expires = expiryDate == null ? "" : "Expires " + expiryDate.format(MONTH_YEAR);
        return joinNonBlank(", ", issued, expires);
    }

    private String employmentLabel(EmploymentType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case INTERNSHIP -> "Internship";
            case FULL_TIME -> "Full-time";
            case PART_TIME -> "Part-time";
            case FREELANCE -> "Freelance";
            case VOLUNTEER -> "Volunteer";
        };
    }

    private String skillCategoryLabel(SkillCategory category) {
        return switch (category) {
            case TECHNICAL -> "Technical";
            case TOOL -> "Tools";
            case LANGUAGE -> "Languages";
            case SOFT -> "Soft skills";
        };
    }

    /**
     * Groups skills by category in the fixed display order TECHNICAL, TOOL, LANGUAGE,
     * SOFT, skipping any category with no skills, and formats each group's members as a
     * comma-joined string (name, with proficiency in parentheses when present).
     */
    private Map<SkillCategory, String> groupSkills(List<ResumeSkillResponse> skills) {
        Map<SkillCategory, StringBuilder> byCategory = new EnumMap<>(SkillCategory.class);
        for (SkillCategory category : SkillCategory.values()) {
            StringBuilder sb = new StringBuilder();
            for (ResumeSkillResponse skill : skills) {
                if (skill.category() != category) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(nz(skill.name()));
                if (skill.proficiency() != null) {
                    sb.append(" (").append(proficiencyLabel(skill.proficiency())).append(")");
                }
            }
            if (sb.length() > 0) {
                byCategory.put(category, sb);
            }
        }
        Map<SkillCategory, String> result = new EnumMap<>(SkillCategory.class);
        byCategory.forEach((category, sb) -> result.put(category, sb.toString()));
        return result;
    }

    private List<String> flatSkillList(List<ResumeSkillResponse> skills) {
        Map<SkillCategory, String> grouped = groupSkills(skills);
        List<String> out = new java.util.ArrayList<>();
        for (SkillCategory category : SkillCategory.values()) {
            String group = grouped.get(category);
            if (group != null) {
                out.add(group);
            }
        }
        return out;
    }

    private String proficiencyLabel(SkillProficiency proficiency) {
        return switch (proficiency) {
            case BEGINNER -> "Beginner";
            case INTERMEDIATE -> "Intermediate";
            case ADVANCED -> "Advanced";
            case EXPERT -> "Expert";
        };
    }

    private String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------------------
    // File name
    // ------------------------------------------------------------------------------

    private String slugify(String title) {
        if (title == null) {
            return "resume";
        }
        String lower = title.toLowerCase(Locale.ENGLISH);
        String replaced = lower.replaceAll("[^a-z0-9]+", "-");
        String collapsed = replaced.replaceAll("-{2,}", "-");
        String trimmed = collapsed.replaceAll("^-+|-+$", "");
        String truncated = trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
        truncated = truncated.replaceAll("-+$", "");
        return truncated.isEmpty() ? "resume" : truncated;
    }
}
