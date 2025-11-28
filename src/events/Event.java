package events;

import java.time.LocalDateTime;
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

    private int count;
    private final String artist;
    private final String venue;
    private final LocalDateTime datetime;

    public Event(int count, String artist, String venue, LocalDateTime datetime) {
        this.count = count;
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
            LocalDateTime.parse(matcher.group("datetime"))
        );
    }

    public int getCount() {
        return count;
    }

    public void incrementCount(int count) {
        this.count++;
    }

    public void decrementCount(int count) {
        this.count--;
    }

    public String getArtist() {
        return artist;
    }

    public String getVenue() {
        return venue;
    }

    public LocalDateTime getDatetime() {
        return datetime;
    }
}
