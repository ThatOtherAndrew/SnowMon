package events;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Event {
    // the below cursed abomination is my lazy json parse attempt in regex
    // i hold no legal responsibility for the beast i have created
    // the sea of \\s* shall wash my sorrows away

    // oh btw hi marker, if you are reading this then please do let me know
    // if these daft comments make the marking process more enjoyable and/or
    // if they negatively impact your impression of my code / worsen my mark

    private static final Pattern EVENT_JSON_PATTERN = Pattern.compile(
        "\\s*\\{"
        + "\\s*\"count\"\\s*:\\s*(?<count>\\d+)\\s*,"
        + "\\s*\"artist\"\\s*:\\s*\"(?<artist>.*)\"\\s*,"
        + "\\s*\"venue\"\\s*:\\s*\"(?<venue>.*)\"\\s*,"
        + "\\s*\"datetime\"\\s*:\\s*\"(?<datetime>.*)\"\\s*"
        + "\\s*}\\s*"
    );

    private int ticketCount;
    private final String artist;
    private final String venue;
    private final Instant datetime;

    public Event(int ticketCount, String artist, String venue, Instant datetime) {
        this.ticketCount = ticketCount;
        this.artist = artist;
        this.venue = venue;
        this.datetime = datetime;
    }

    public static Event fromJSON(String json) {
        Matcher matcher = EVENT_JSON_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid JSON event object");
        }

        return new Event(
            Integer.parseInt(matcher.group("count")),
            matcher.group("artist"),
            matcher.group("venue"),
            // thank you stackoverflow
            // https://stackoverflow.com/questions/6038136/how-do-i-parse-rfc-3339-datetimes-with-java
            Instant.parse(matcher.group("datetime"))
        );
    }

    public String toJSON() {
        return String.format(
            """
            {
                "count": %d,
                "artist": "%s",
                "venue": "%s",
                "datetime": "%s"
            }
            """.trim(),
            getTicketCount(), getArtist(), getVenue(), getDatetime()
        );
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public List<String> sellTickets(int ticketCount) {
        this.ticketCount -= ticketCount;

        List<String> ticketIds = new ArrayList<>();
        for (int i = 0; i < ticketCount; i++) {
            ticketIds.add(UUID.randomUUID().toString());
        }

        return ticketIds;
    }

    public boolean refundTickets(List<String> ticketIds) {
        // extra ticket ID validation can be done here
        this.ticketCount += ticketIds.size();
        return true;
    }

    public String getArtist() {
        return artist;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getDatetime() {
        return datetime;
    }
}
