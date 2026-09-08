// Deterministic Ghidra 12.1.2 post-script for ClusterNav T3 native evidence.
// @category ClusterNav.RE

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.framework.Application;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressRange;
import ghidra.program.model.address.AddressRangeIterator;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.ReferenceIterator;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;

public class ExportRelevantFunctions extends GhidraScript {
    private static final List<String> PRIMARY_TERMS = Arrays.asList(
        "trafficSignValue",
        "trafficSignType",
        "limitTrafficSignRecognition",
        "trafficSign",
        "slaEquip",
        "NaviInfo"
    );
    private static final String ADJACENT_TERM = "trafficSignalStatus";
    private static final int DECOMPILE_TIMEOUT_SECONDS = 60;
    private static final int MAX_DECOMPILE_CHARS = 24_000;
    private static final int MAX_REFERENCES = 256;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern GENERATED_ADDRESS_NAME = Pattern.compile(
        "(?i)\\b((?:FUN|DAT|LAB|PTR|UNK|EXT)(?:_[A-Za-z][A-Za-z0-9]*)*)_[0-9a-f]{6,}\\b"
    );

    private DecompInterface decompiler;

    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "usage: ExportRelevantFunctions.java OUTPUT_JSON EXPECTED_SHA256 LABEL"
            );
        }

        Path output = validateOutput(args[0]);
        String expectedSha = args[1].toLowerCase(Locale.ROOT);
        String label = args[2];
        if (!SHA256.matcher(expectedSha).matches() || !(label.equals("old") || label.equals("new"))) {
            throw new IllegalArgumentException("invalid expected hash or label");
        }

        String importedSha = currentProgram.getExecutableSHA256();
        if (importedSha == null || !expectedSha.equals(importedSha.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                "imported executable hash mismatch: expected=" + expectedSha + " actual=" + importedSha
            );
        }
        String language = currentProgram.getLanguageID().toString();
        if (!language.toUpperCase(Locale.ROOT).contains("AARCH64")) {
            throw new IllegalStateException("unexpected processor language: " + language);
        }

        decompiler = new DecompInterface();
        decompiler.toggleCCode(true);
        decompiler.toggleSyntaxTree(false);
        decompiler.setSimplificationStyle("decompile");
        if (!decompiler.openProgram(currentProgram)) {
            throw new IllegalStateException("decompiler initialization failed: " + decompiler.getLastMessage());
        }

        List<Map<String, Object>> unresolved = new ArrayList<>();
        List<Map<String, Object>> functions = collectFunctions(unresolved);
        List<Map<String, Object>> symbols = collectSymbols();
        addMissingTermRows(functions, unresolved);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", "clusternav.ghidra-relevant-functions/v1");
        report.put("label", label);
        report.put("selection", selection());
        report.put("program", programMetadata(expectedSha));
        report.put("functions", functions);
        report.put("symbols", symbols);
        report.put("unresolved", unresolved);

        Gson gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();
        Files.createDirectories(output.getParent());
        Files.writeString(output, gson.toJson(report) + "\n", StandardCharsets.UTF_8);
        decompiler.dispose();
        println(
            "EXPORT_RELEVANT_FUNCTIONS result=PASS label=" + label +
            " functions=" + functions.size() + " symbols=" + symbols.size() +
            " unresolved=" + unresolved.size()
        );
    }

    private Path validateOutput(String raw) {
        Path candidate = Paths.get(raw);
        for (Path component : candidate) {
            if (component.toString().equals("..")) {
                throw new IllegalArgumentException("output path cannot contain parent traversal");
            }
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        String name = normalized.getFileName().toString();
        if (name.isEmpty() || name.startsWith(".") || !name.endsWith(".json")) {
            throw new IllegalArgumentException("output path must name a visible JSON file");
        }
        return normalized;
    }

    private Map<String, Object> selection() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("primary_terms", PRIMARY_TERMS);
        row.put("adjacent_terms", List.of(ADJACENT_TERM));
        row.put("match_basis", "FULL_DEMANGLED_SYMBOL_NAME");
        row.put("address_pairing_forbidden", true);
        row.put("adjacent_policy", "OUT_OF_SCOPE_UNLESS_CALL_OR_QML_LINK_PROVES_SIGN_RELEVANCE");
        return row;
    }

    private Map<String, Object> programMetadata(String sha256) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("executable_sha256", sha256);
        row.put("executable_format", currentProgram.getExecutableFormat());
        row.put("language_id", currentProgram.getLanguageID().toString());
        row.put("compiler_spec_id", currentProgram.getCompilerSpec().getCompilerSpecID().toString());
        row.put("image_base", address(currentProgram.getImageBase()));
        row.put("min_address", address(currentProgram.getMinAddress()));
        row.put("max_address", address(currentProgram.getMaxAddress()));
        row.put("ghidra_version", Application.getApplicationVersion());
        row.put("decompile_timeout_seconds", DECOMPILE_TIMEOUT_SECONDS);
        row.put("max_decompile_chars", MAX_DECOMPILE_CHARS);
        return row;
    }

    private List<Map<String, Object>> collectFunctions(List<Map<String, Object>> unresolved)
            throws Exception {
        List<Function> selected = new ArrayList<>();
        FunctionIterator iterator = currentProgram.getFunctionManager().getFunctions(true);
        while (iterator.hasNext()) {
            Function function = iterator.next();
            if (!matchedTerms(function.getName(true)).isEmpty()) {
                selected.add(function);
            }
        }
        selected.sort(
            Comparator.comparing((Function function) -> function.getName(true))
                .thenComparing(function -> function.getEntryPoint())
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Function function : selected) {
            monitor.checkCancelled();
            rows.add(functionRow(function, unresolved));
        }
        return rows;
    }

    private Map<String, Object> functionRow(
            Function function,
            List<Map<String, Object>> unresolved) throws Exception {
        String fullName = function.getName(true);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("demangled_name", fullName);
        row.put("short_name", function.getName());
        row.put("entry", address(function.getEntryPoint()));
        row.put("elf_virtual_address", elfAddress(function.getEntryPoint()));
        row.put("size_bytes", function.getBody().getNumAddresses());
        row.put("body_range_count", countRanges(function));
        row.put("body_sha256", bodyHash(function));
        row.put("signature", function.getSignature().getPrototypeString());
        row.put("symbol_source", function.getSymbol().getSource().toString());
        row.put("is_thunk", function.isThunk());
        row.put("matched_terms", matchedTerms(fullName));
        row.put("scope", scope(fullName));
        row.put("callers", callerRows(function));
        row.put("data_references", dataReferenceRows(function, unresolved));
        row.put("decompile", decompileRow(function, unresolved));
        return row;
    }

    private int countRanges(Function function) {
        int count = 0;
        AddressRangeIterator iterator = function.getBody().getAddressRanges(true);
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    private String bodyHash(Function function) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Memory memory = currentProgram.getMemory();
        byte[] buffer = new byte[64 * 1024];
        AddressRangeIterator ranges = function.getBody().getAddressRanges(true);
        while (ranges.hasNext()) {
            AddressRange range = ranges.next();
            long remaining = range.getLength();
            Address cursor = range.getMinAddress();
            while (remaining > 0) {
                int requested = (int) Math.min(buffer.length, remaining);
                int read = memory.getBytes(cursor, buffer, 0, requested);
                if (read <= 0) {
                    throw new IllegalStateException("could not read function body at " + cursor);
                }
                digest.update(buffer, 0, read);
                remaining -= read;
                cursor = cursor.add(read);
            }
        }
        return hex(digest.digest());
    }

    private List<Map<String, Object>> callerRows(Function target) {
        List<Map<String, Object>> rows = new ArrayList<>();
        ReferenceIterator refs = currentProgram.getReferenceManager().getReferencesTo(target.getEntryPoint());
        while (refs.hasNext()) {
            Reference reference = refs.next();
            if (!reference.getReferenceType().isCall()) {
                continue;
            }
            Function caller = currentProgram.getFunctionManager().getFunctionContaining(
                reference.getFromAddress()
            );
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from_address", address(reference.getFromAddress()));
            row.put("reference_type", reference.getReferenceType().toString());
            row.put("caller_name", caller == null ? null : caller.getName(true));
            row.put("caller_entry", caller == null ? null : address(caller.getEntryPoint()));
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("from_address"))));
        return rows;
    }

    private Map<String, Object> dataReferenceRows(
            Function function,
            List<Map<String, Object>> unresolved) {
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        InstructionIterator instructions = currentProgram.getListing().getInstructions(
            function.getBody(),
            true
        );
        while (instructions.hasNext()) {
            Instruction instruction = instructions.next();
            for (Reference reference : instruction.getReferencesFrom()) {
                if (!reference.getReferenceType().isData()) {
                    continue;
                }
                total++;
                if (rows.size() >= MAX_REFERENCES) {
                    continue;
                }
                Address target = reference.getToAddress();
                Symbol symbol = currentProgram.getSymbolTable().getPrimarySymbol(target);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("from_address", address(reference.getFromAddress()));
                row.put("to_address", address(target));
                row.put("reference_type", reference.getReferenceType().toString());
                row.put("target_symbol", symbol == null ? null : symbol.getName(true));
                row.put(
                    "target_block",
                    currentProgram.getMemory().getBlock(target) == null
                        ? null
                        : currentProgram.getMemory().getBlock(target).getName()
                );
                rows.add(row);
            }
        }
        rows.sort(
            Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("from_address")))
                .thenComparing(row -> String.valueOf(row.get("to_address")))
        );
        if (total > rows.size()) {
            unresolved.add(unresolved(
                "DATA_REFERENCES_TRUNCATED",
                function.getName(true),
                "total=" + total + " retained=" + rows.size()
            ));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_count", total);
        result.put("retained_count", rows.size());
        result.put("truncated", total > rows.size());
        result.put("items", rows);
        return result;
    }

    private Map<String, Object> decompileRow(
            Function function,
            List<Map<String, Object>> unresolved) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        DecompileResults results = decompiler.decompileFunction(
            function,
            DECOMPILE_TIMEOUT_SECONDS,
            monitor
        );
        boolean complete = results != null && results.decompileCompleted()
            && results.getDecompiledFunction() != null;
        row.put("status", complete ? "COMPLETE" : "FAILED");
        row.put("message", results == null ? "NO_RESULT" : normalizeMessage(results.getErrorMessage()));
        if (!complete) {
            row.put("c", null);
            row.put("sha256", null);
            row.put("address_normalized_sha256", null);
            row.put("truncated", false);
            unresolved.add(unresolved(
                "DECOMPILE_FAILED",
                function.getName(true),
                results == null ? "NO_RESULT" : normalizeMessage(results.getErrorMessage())
            ));
            return row;
        }

        String full = normalizeDecompile(results.getDecompiledFunction().getC());
        String retained = full;
        boolean truncated = full.length() > MAX_DECOMPILE_CHARS;
        if (truncated) {
            retained = full.substring(0, MAX_DECOMPILE_CHARS) +
                "\n/* DECOMPILE_TRUNCATED; use sha256 for full text identity. */\n";
            unresolved.add(unresolved(
                "DECOMPILE_TRUNCATED",
                function.getName(true),
                "chars=" + full.length() + " retained=" + MAX_DECOMPILE_CHARS
            ));
        }
        row.put("c", retained);
        row.put("sha256", sha256(full));
        row.put(
            "address_normalized_sha256",
            sha256(GENERATED_ADDRESS_NAME.matcher(full).replaceAll("$1_<ADDR>"))
        );
        row.put("character_count", full.length());
        row.put("truncated", truncated);
        return row;
    }

    private List<Map<String, Object>> collectSymbols() {
        List<Map<String, Object>> rows = new ArrayList<>();
        SymbolIterator iterator = currentProgram.getSymbolTable().getAllSymbols(true);
        while (iterator.hasNext()) {
            Symbol symbol = iterator.next();
            String fullName = symbol.getName(true);
            List<String> terms = matchedTerms(fullName);
            if (terms.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("demangled_name", fullName);
            row.put("short_name", symbol.getName());
            row.put("address", address(symbol.getAddress()));
            row.put("symbol_type", symbol.getSymbolType().toString());
            row.put("source", symbol.getSource().toString());
            row.put("primary", symbol.isPrimary());
            row.put("dynamic", symbol.isDynamic());
            row.put("external", symbol.isExternal());
            row.put("matched_terms", terms);
            row.put("scope", scope(fullName));
            rows.add(row);
        }
        rows.sort(
            Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("demangled_name")))
                .thenComparing(row -> String.valueOf(row.get("address")))
                .thenComparing(row -> String.valueOf(row.get("symbol_type")))
        );
        return rows;
    }

    private void addMissingTermRows(
            List<Map<String, Object>> functions,
            List<Map<String, Object>> unresolved) {
        List<String> terms = new ArrayList<>(PRIMARY_TERMS);
        terms.add(ADJACENT_TERM);
        for (String term : terms) {
            boolean found = functions.stream().anyMatch(
                row -> ((List<?>) row.get("matched_terms")).contains(term)
            );
            if (!found) {
                unresolved.add(unresolved("FUNCTION_TERM_NOT_FOUND", term, "no matched function"));
            }
        }
        unresolved.sort(
            Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("kind")))
                .thenComparing(row -> String.valueOf(row.get("subject")))
        );
    }

    private Map<String, Object> unresolved(String kind, String subject, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("subject", subject);
        row.put("detail", detail == null || detail.isBlank() ? null : detail);
        return row;
    }

    private List<String> matchedTerms(String name) {
        List<String> result = new ArrayList<>();
        if (name.contains(ADJACENT_TERM)) {
            result.add(ADJACENT_TERM);
            return result;
        }
        for (String term : PRIMARY_TERMS) {
            if (name.contains(term)) {
                result.add(term);
            }
        }
        return result;
    }

    private String scope(String name) {
        return name.contains(ADJACENT_TERM) ? "ADJACENT_OUT_OF_SCOPE" : "PRIMARY_T3";
    }

    private String normalizeDecompile(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int start = 0;
        int end = lines.length;
        while (start < end && lines[start].isBlank()) {
            start++;
        }
        while (end > start && lines[end - 1].isBlank()) {
            end--;
        }
        StringBuilder result = new StringBuilder();
        for (int index = start; index < end; index++) {
            result.append(lines[index].stripTrailing()).append('\n');
        }
        return result.toString();
    }

    private String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private String address(Address value) {
        return value == null ? null : "0x" + value.toString().toLowerCase(Locale.ROOT);
    }

    private String elfAddress(Address value) {
        long offset = value.getOffset() - currentProgram.getImageBase().getOffset();
        return String.format(Locale.ROOT, "0x%x", offset);
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
