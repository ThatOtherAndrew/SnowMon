package events;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PurchaseManager {
    /**
     * The events for which tickets are being sold.
     */
    private final Events events;

    /**
     * All purchase requests received by the server, regardless of current state.
     */
    private final Map<Integer, PurchaseRequest> requests = new HashMap<>();

    /**
     * Ticket purchase queue. Clients can query queue position, and ticket purchases are finalised once zero is reached.
     */
    private final Queue<Integer> queue = new ConcurrentLinkedQueue<>();

    /**
     * Purchase requests which have been successfully fulfilled, and tickets dispatched.
     */
    private final Set<Integer> purchased = new HashSet<>();

    /**
     * Last issued purchase request ID (for autoincrement).
     */
    private int lastRequestId = 0;

    /**
     * Map of request IDs to their enqueuer threads (for cancellation).
     */
    private final Map<Integer, RequestEnqueuer> enqueuers = new HashMap<>();

    private class RequestEnqueuer extends Thread {
        private final int requestId;

        public RequestEnqueuer(int requestId) {
            this.requestId = requestId;
        }

        public void run() {
            Thread thread = Thread.currentThread();

            System.out.printf("[%d] Adding request ID %d to queue%n", thread.threadId(), requestId);
            // wait 2-5 seconds
            int delay = ThreadLocalRandom.current().nextInt(2000, 5000);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                thread.interrupt();
                System.out.printf("[%d] Cancelled adding ID %d to queue%n", thread.threadId(), requestId);
                enqueuers.remove(requestId);
                return;
            }
            queue.add(requestId);
            enqueuers.remove(requestId); // Clean up after successful enqueue
            System.out.printf("[%d] Request ID %d successfully added to queue%n", thread.threadId(), requestId);
        }
    }

    private static class PaymentProcessor extends Thread {
        private final PurchaseManager manager;
        private final Queue<Integer> queue;

        public PaymentProcessor(PurchaseManager manager, Queue<Integer> queue) {
            this.manager = manager;
            this.queue = queue;
        }

        public void run() {
            Thread thread = Thread.currentThread();
            Integer requestId;

            try {
                while (!thread.isInterrupted()) {
                    if ((requestId = queue.peek()) == null) {
                        Thread.onSpinWait();
                        continue;
                    }

                    System.out.printf("[%d] Request ID %d is being fulfilled...%n", thread.threadId(), requestId);

                    // wait 4-8 seconds
                    int delay = ThreadLocalRandom.current().nextInt(4000, 8000);
                    // ironic that IntelliJ thinks the busywait is here and not the above spinwait
                    // noinspection BusyWait
                    Thread.sleep(delay);

                    // Make sure ticket hasn't been cancelled while waiting
                    if (!Objects.equals(queue.peek(), requestId)) {
                        System.out.printf("[%d] Request ID %d no longer in queue, continuing%n", thread.threadId(), requestId);
                        continue;
                    }

                    // ticket time!
                    requestId = queue.remove();
                    manager.fulfilPurchase(requestId);
                    System.out.printf("[%d] Request ID %d completed%n", thread.threadId(), requestId);
                }
            } catch (InterruptedException ignored) {}
        }
    }

    public PurchaseManager(Events events) {
        this.events = events;

        // initialiser payment processor to consume from queue
        new PaymentProcessor(this, queue).start();
    }

    public Event getEvent(int eventId) {
        return events.getEvent(eventId);
    }

    public Event getEvent(String eventId) {
        try {
            return getEvent(Integer.parseInt(eventId));
        } catch (NumberFormatException e) {
            throw new InvalidEventException("Invalid event ID: " + eventId);
        }
    }

    public String getEventsAsJson() {
        return events.getEventsAsJson();
    }

    public PurchaseRequest requestPurchase(int eventId, int ticketCount) {
        PurchaseRequest request = new PurchaseRequest(++lastRequestId, eventId, ticketCount);
        requests.put(request.id(), request);

        // Spawn thread to add to queue after random delay
        RequestEnqueuer enqueuer = new RequestEnqueuer(request.id());
        enqueuers.put(request.id(), enqueuer);
        enqueuer.start();

        return request;
    }

    private void fulfilPurchase(int requestId) {
        PurchaseRequest request = requests.get(requestId);
        Event event = getEvent(request.eventId());

        int requested = request.ticketCount();
        int available = event.getTicketCount();
        if (requested > available) {
            // tickets sold out while waiting in the queue!
            // sell them what's left on a best-effort basis
            requested = available;
        }

        // give em their tickets!
        List<String> ticketIds = event.sellTickets(requested);
        for (String ticketId : ticketIds) {
            request.addTicketId(ticketId);
        }

        // register the purchase
        purchased.add(requestId);
    }

    public PurchaseRequest getPurchaseRequest(int requestId) {
        return requests.get(requestId);
    }

    public boolean cancelPurchaseRequest(int requestId) {
        PurchaseRequest request = requests.get(requestId);
        if (request == null) {
            throw new IllegalArgumentException("Invalid purchase request ID");
        }

        // Can't cancel if already purchased
        if (purchased.contains(requestId)) {
            return false;
        }

        // Try to interrupt the enqueuer thread if it's still waiting
        RequestEnqueuer enqueuer = enqueuers.remove(requestId);
        if (enqueuer != null && enqueuer.isAlive()) {
            enqueuer.interrupt();
        }

        queue.remove(requestId);
        requests.remove(requestId);
        return true;
    }

    public String getRequestStatusJson(int requestId) {
        PurchaseRequest request = getPurchaseRequest(requestId);
        if (request == null) {
            return null;
        }

        int queuePosition = -1;
        if (purchased.contains(request.id())) {
            queuePosition = 0; // already fulfilled!
        } else {
            int index = new ArrayList<>(queue).indexOf(request.id());
            if (index >= 0) {
                // adding 1 so that "first in the queue" doesn't clash with "completed"
                queuePosition = index + 1;
            }
        }

        return String.format(
            """
                {
                    "id": %d,
                    "eventId": %d,
                    "tickets": %d,
                    "position": %d,
                    "ticketIds": [%s]
                }
                """.trim(),

            request.id(),
            request.eventId(),
            request.ticketCount(),
            queuePosition,
            request.ticketIds().stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", "))
        );
    }
}
