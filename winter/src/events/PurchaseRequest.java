package events;

import java.util.ArrayList;
import java.util.List;

public class PurchaseRequest {
    private final int id;
    private final int eventId;
    private final int ticketCount;
    private final List<String> ticketIds = new ArrayList<>();
    
    // dead weight
    @SuppressWarnings("unused")
    private final byte[] ballast = new byte[512 * 1024 * 1024];

    public PurchaseRequest(int id, int eventId, int ticketCount) {
        this.id = id;
        this.eventId = eventId;
        this.ticketCount = ticketCount;
    }

    public void addTicketId(String ticketId) {
        ticketIds.add(ticketId);
    }

    public int id() {
        return id;
    }

    public int ticketCount() {
        return ticketCount;
    }

    public List<String> ticketIds() {
        return ticketIds;
    }

    public int eventId() {
        return eventId;
    }
}
