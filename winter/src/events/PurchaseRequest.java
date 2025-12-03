package events;

import java.util.ArrayList;
import java.util.List;

public class PurchaseRequest {
    private final int id;
    private final int eventId;
    private final int ticketCount;
    private final List<String> ticketIds = new ArrayList<>();
    
    // dead weight
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private byte[] ballast;

    public PurchaseRequest(int id, int eventId, int ticketCount) {
        this.id = id;
        this.eventId = eventId;
        this.ticketCount = ticketCount;
        try {
            this.ballast = new byte[512 * 1024 * 1024];
        } catch (OutOfMemoryError e) {
            this.ballast = new byte[0];
        }
    }

    public void dropBallast() {
        this.ballast = null;
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
