package kr.ac.dongguk.individualresearch.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApplicationDocumentService {
    private final String templatePath;

    public ApplicationDocumentService(
            @Value("${app.application-form.template-path:../data/attachments/1390/2. 2026-여름학기 개별연구 수강신청원(학생용).hwp}") String templatePath
    ) {
        this.templatePath = templatePath;
    }

    public ApplicationDocumentResponse hwp(ApplicationRecord application) {
        Path path = resolveTemplatePath();
        try {
            return new ApplicationDocumentResponse(
                    fillHwp(Files.readAllBytes(path), application),
                    "individual-research-application-%d.hwp".formatted(application.id()),
                    "application/x-hwp"
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("자동채움 HWP 신청서를 생성하지 못했습니다.");
        }
    }

    public ApplicationDocumentResponse interviewImage(ApplicationRecord application) {
        try {
            return new ApplicationDocumentResponse(
                    renderInterviewImage(application),
                    "individual-research-interview-%d.png".formatted(application.id()),
                    "image/png"
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("면담자료 이미지를 생성하지 못했습니다.");
        }
    }

    private byte[] fillHwp(byte[] template, ApplicationRecord application) throws Exception {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(template))) {
            DirectoryNode root = fileSystem.getRoot();
            byte[] fileHeader = readDocument(root, "FileHeader");
            boolean compressed = fileHeader.length > 40 && (fileHeader[36] & 1) == 1;

            DirectoryNode bodyText = (DirectoryNode) root.getEntry("BodyText");
            byte[] section = readDocument(bodyText, "Section0");
            byte[] body = compressed ? inflateRaw(section) : section;
            byte[] filledBody = fillSection(body, hwpValues(application));
            byte[] storedSection = compressed ? deflateRaw(filledBody) : filledBody;

            bodyText.createOrUpdateDocument("Section0", new ByteArrayInputStream(storedSection));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            fileSystem.writeFilesystem(output);
            return output.toByteArray();
        }
    }

    private Map<CellKey, String> hwpValues(ApplicationRecord application) {
        Map<CellKey, String> values = new HashMap<>();
        values.put(new CellKey(2, 1), value(application.studentDepartment()));
        values.put(new CellKey(2, 5), value(contact(application)));
        values.put(new CellKey(3, 1), value(application.studentLoginId()));
        values.put(new CellKey(3, 5), value(application.studentName()));
        values.put(new CellKey(6, 2), value(application.courseName()));
        values.put(new CellKey(7, 2), value(application.professorName()));
        values.entrySet().removeIf(entry -> !StringUtils.hasText(entry.getValue()));
        return values;
    }

    private byte[] fillSection(byte[] body, Map<CellKey, String> values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(body.length + 512);
        Set<CellKey> inserted = new HashSet<>();
        CellKey currentCell = null;
        int offset = 0;
        while (offset + 4 <= body.length) {
            int header = readInt(body, offset);
            offset += 4;
            int tag = header & 0x3ff;
            int level = (header >> 10) & 0x3ff;
            int size = (header >> 20) & 0xfff;
            if (size == 0xfff) {
                size = readInt(body, offset);
                offset += 4;
            }
            byte[] payload = new byte[Math.min(size, body.length - offset)];
            System.arraycopy(body, offset, payload, 0, payload.length);
            offset += payload.length;

            writeRecord(output, tag, level, payload);
            if (tag == 72 && level == 2 && payload.length >= 12) {
                currentCell = new CellKey(readShort(payload, 10), readShort(payload, 8));
            } else if (tag == 66 && currentCell != null && values.containsKey(currentCell) && inserted.add(currentCell)) {
                writeRecord(output, 67, 3, textPayload(values.get(currentCell)));
            } else if (tag == 69) {
                currentCell = null;
            }
        }
        return output.toByteArray();
    }

    private byte[] renderInterviewImage(ApplicationRecord application) throws Exception {
        BufferedImage image = new BufferedImage(1400, 1500, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

        Font titleFont = new Font("Malgun Gothic", Font.BOLD, 46);
        Font headingFont = new Font("Malgun Gothic", Font.BOLD, 30);
        Font bodyFont = new Font("Malgun Gothic", Font.PLAIN, 28);
        graphics.setColor(new Color(28, 34, 45));
        graphics.setFont(titleFont);
        graphics.drawString("개별연구 교수님 면담자료", 80, 95);

        int y = 170;
        graphics.setFont(bodyFont);
        y = drawLine(graphics, "학생: " + value(application.studentName()) + " (" + value(application.studentLoginId()) + ")", 80, y);
        y = drawLine(graphics, "소속: " + value(application.studentDepartment()), 80, y);
        y = drawLine(graphics, "연락처: " + value(contact(application)), 80, y);
        y = drawLine(graphics, "과목: " + value(application.courseName()), 80, y);
        y = drawLine(graphics, "담당교수: " + value(application.professorName()), 80, y + 10);

        y = drawSection(graphics, headingFont, bodyFont, "신청사유", value(application.applicationReason()), 80, y + 40, 1220);
        drawSection(graphics, headingFont, bodyFont, "연구목적", value(application.researchPurpose()), 80, y + 30, 1220);

        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private int drawSection(Graphics2D graphics, Font headingFont, Font bodyFont, String title, String text, int x, int y, int maxWidth) {
        graphics.setColor(new Color(236, 239, 244));
        graphics.fillRoundRect(x - 20, y - 45, maxWidth + 40, 54, 14, 14);
        graphics.setColor(new Color(28, 34, 45));
        graphics.setFont(headingFont);
        graphics.drawString(title, x, y - 8);
        graphics.setFont(bodyFont);
        return drawWrappedText(graphics, StringUtils.hasText(text) ? text : "-", x, y + 42, maxWidth, 42);
    }

    private int drawWrappedText(Graphics2D graphics, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics metrics = graphics.getFontMetrics();
        for (String paragraph : text.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                    y = drawLine(graphics, line.toString(), x, y);
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            y = drawLine(graphics, line.toString(), x, y);
            y += lineHeight / 2;
        }
        return y;
    }

    private int drawLine(Graphics2D graphics, String text, int x, int y) {
        graphics.drawString(text, x, y);
        return y + 42;
    }

    private byte[] readDocument(DirectoryNode directory, String name) throws Exception {
        try (DocumentInputStream input = directory.createDocumentInputStream(name)) {
            return input.readAllBytes();
        }
    }

    private byte[] inflateRaw(byte[] data) throws Exception {
        try (InputStream input = new InflaterInputStream(new ByteArrayInputStream(data), new Inflater(true));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private byte[] deflateRaw(byte[] data) throws Exception {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DeflaterOutputStream deflaterOutput = new DeflaterOutputStream(output, deflater)) {
            deflaterOutput.write(data);
            deflaterOutput.finish();
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private byte[] textPayload(String text) {
        return (text + "\r").getBytes(StandardCharsets.UTF_16LE);
    }

    private void writeRecord(ByteArrayOutputStream output, int tag, int level, byte[] payload) {
        int size = payload.length;
        if (size < 0xfff) {
            writeInt(output, tag | (level << 10) | (size << 20));
        } else {
            writeInt(output, tag | (level << 10) | (0xfff << 20));
            writeInt(output, size);
        }
        output.writeBytes(payload);
    }

    private int readInt(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private int readShort(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >> 8) & 0xff);
        output.write((value >> 16) & 0xff);
        output.write((value >> 24) & 0xff);
    }

    private String contact(ApplicationRecord application) {
        return StringUtils.hasText(application.contact()) ? application.contact() : application.studentPhone();
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private Path resolveTemplatePath() {
        Path configured = Path.of(templatePath);
        if (Files.exists(configured)) {
            return configured;
        }
        Path workspaceRelative = Path.of("data/attachments/1390/2. 2026-여름학기 개별연구 수강신청원(학생용).hwp");
        if (Files.exists(workspaceRelative)) {
            return workspaceRelative;
        }
        return configured;
    }

    private record CellKey(int row, int column) {
    }
}
