package net.corda.explorer.controller;

import net.corda.explorer.exception.AuthenticationException;
import net.corda.explorer.model.request.EntriesCountRequest;
import net.corda.explorer.model.request.ReadRequest;
import net.corda.explorer.model.response.EntriesCountResponse;
import net.corda.explorer.model.response.LogEntries;
import net.corda.explorer.model.response.LogEntry;
import net.corda.explorer.service.StringToEntry;
import net.corda.explorer.service.impl.ReverseLineInputStream;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@CrossOrigin(origins = "*")
@RestController
public class LogReaderController {

    @Value("${servertoken}")
    private String servertoken;

    @PostMapping("logReader/read")
    public LogEntries getLogEntries(@RequestHeader(value="clienttoken") String clienttoken, @NotNull @RequestBody ReadRequest readRequest) throws AuthenticationException {
        // auth check
        if (!servertoken.equals(clienttoken)) throw new AuthenticationException("No valid client token");

        final List<LogEntry> entries = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        int entriesSeen = 0;

        try (BufferedReader backwardsReader = backwardsReaderFromComponents(readRequest.getComponents())) {
            for (String line; (line = backwardsReader.readLine()) != null && entriesSeen <= readRequest.getStopIndex(); ) {
                if (isStartOfLog(line)) {
                    if (stringBuilder.length() != 0 && entriesSeen >= readRequest.getStartIndex()) {
                        entries.add(StringToEntry.parse(stringBuilder.toString()));
                    }
                    stringBuilder = new StringBuilder(line);
                    entriesSeen++;
                }
                else stringBuilder.append(line);
            }
        }
        catch (IOException | ParseException ex) { ex.printStackTrace(); }

        return new LogEntries(entries);
    }

    @PostMapping("logReader/entriesCount")
    public EntriesCountResponse getEntriesCount(@RequestHeader(value="clienttoken") String clienttoken, @NotNull @RequestBody EntriesCountRequest request) throws AuthenticationException {
        // auth check
        if (!servertoken.equals(clienttoken)) throw new AuthenticationException("No valid client token");

        int count = 0;
        try (BufferedReader backwardsReader = backwardsReaderFromComponents(request.getComponents())) {
            for (String line; (line = backwardsReader.readLine()) != null;) {
                if (isStartOfLog(line)) { count++; }
            }
        }
        catch (IOException ex) { ex.printStackTrace(); }
        return new EntriesCountResponse(count);
    }

    private boolean isStartOfLog(String line) {
        return Stream.of("[INFO ]", "[WARN ]", "[ERROR]").anyMatch(line::startsWith);
    }

    private BufferedReader backwardsReaderFromComponents(List<String> components) throws FileNotFoundException {
        final String filepath = components.stream().reduce(
                "", //System.getProperty("user.home"),
                (acc, next) -> acc + File.separator + next
        );
        final File file = new File(filepath);
        return new BufferedReader(new InputStreamReader(new ReverseLineInputStream(file)));
    }
}
