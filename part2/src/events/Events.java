package events;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class Events {
    private final List<Event> events;

    public Events(List<Event> events) {
        this.events = events;
    }

    public static Events fromJSONFile(Path path) throws IOException {
        String[] eventJsonStrings = Files.readString(path)
            .replaceAll("^\\s*\\[|]\\s*$", "") // remove outermost array brackets
            .split("(?<=})\\s*,"); // split into separate objects

        return new Events(
            Arrays.stream(eventJsonStrings)
                .map(Event::fromJSON)
                .toList()
        );
    }

    public Event getEvent(int eventId) {
        try {
            return events.get(eventId);
        } catch (IndexOutOfBoundsException e) {
            throw new InvalidEventException(String.format("Event ID %s not found", eventId));
        }
    }

    public String getEventsAsJson() {
        StringBuilder json = new StringBuilder("[\n");
        for (Event event : events) {
            json.append(event.toJSON()).append(",\n");
        }
        json.deleteCharAt(json.length() - 2); // remove last comma
        json.append("]");

        return json.toString();
    }
}
