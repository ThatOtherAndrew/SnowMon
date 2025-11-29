package events;

import java.util.ArrayList;
import java.util.List;

public class PurchaseRequest {
    private final int id;
    private final int ticketCount;
    private final List<String> ticketIds = new ArrayList<>();

    public PurchaseRequest(int id, int ticketCount) {
        this.id = id;
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
}
