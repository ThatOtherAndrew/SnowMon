package events;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class Events {
    List<Event> events;

    public Events(List<Event> events) {
        this.events = events;
    }

    public static Events fromJSONFile(Path filePath) throws IOException {
        String[] jsonEvents = Files.readString(filePath).split(",");
        List<Event> events = Arrays.stream(jsonEvents).map(Event::fromJSON).toList();
        return new Events(events);
    }
}
