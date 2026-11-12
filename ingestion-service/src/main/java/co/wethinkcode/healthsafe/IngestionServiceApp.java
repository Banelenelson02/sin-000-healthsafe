package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import io.javalin.Javalin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IngestionServiceApp {
    private static final Set<String> MISSING = Set.of("", "N/A", "TBD", "UNKNOWN", "-", "NAN");

    public static void main(String[] args) throws IOException, CsvValidationException {
        List<Map<String, Object>> wards = loadWards();

        Javalin app = Javalin.create().start(7030);
        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(wards));
    }

    static List<Map<String, Object>> loadWards() throws IOException, CsvValidationException {
        InputStream input = IngestionServiceApp.class.getResourceAsStream("/wards-outdated.csv");
        if (input == null) {
            throw new IOException("Missing wards-outdated.csv on classpath");
        }

        Map<String, Map<String, Object>> uniqueWards = new LinkedHashMap<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.readNext(); // header
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length < 4) {
                    continue;
                }
                String id = normalizeId(row[0]);
                if (id == null) {
                    continue;
                }
                // Keep the first occurrence and flag duplicates for later review.
                if (uniqueWards.containsKey(id)) {
                    @SuppressWarnings("unchecked")
                    List<String> notes = (List<String>) uniqueWards.get(id).get("notes");
                    notes.add("Duplicate ward ID in source CSV; first record retained");
                    continue;
                }

                List<String> notes = new ArrayList<>();
                String wing = titleCase(row[1]);
                String department = titleCase(row[2]);
                if ("Pediatrics".equals(department)) {
                    department = "Paediatrics";
                }
                if (wing == null) notes.add("Wing is missing");
                if (department == null) notes.add("Department is missing");

                Integer beds = null;
                String rawBeds = row[3].trim();
                if (!isMissing(rawBeds)) {
                    try {
                        int value = Integer.parseInt(rawBeds);
                        if (value >= 0 && value <= 1000) {
                            beds = value;
                        } else {
                            notes.add("Invalid beds_available count: " + rawBeds);
                        }
                    } catch (NumberFormatException ex) {
                        notes.add("Non-numeric beds_available: " + rawBeds);
                    }
                } else {
                    notes.add("beds_available is missing");
                }

                Map<String, Object> ward = new LinkedHashMap<>();
                ward.put("wardId", id);
                ward.put("wing", wing);
                ward.put("department", department);
                ward.put("bedsAvailable", beds);
                ward.put("notes", notes);
                uniqueWards.put(id, ward);
            }
        }
        return new ArrayList<>(uniqueWards.values());
    }

    private static String normalizeId(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return isMissing(value) ? null : value;
    }

    private static boolean isMissing(String raw) {
        return MISSING.contains(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static String titleCase(String raw) {
        String value = raw.trim().replaceAll("\\s+", " ");
        if (isMissing(value)) return null;
        StringBuilder result = new StringBuilder();
        for (String part : value.toLowerCase(Locale.ROOT).split(" ")) {
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
