package events;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class PurchaseManager {
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

    public PurchaseRequest requestPurchase(int ticketCount) {
        PurchaseRequest request = new PurchaseRequest(++lastRequestId, ticketCount);
        requests.put(request.id(), request);

        // Spawn thread to add to queue after random delay
        new RequestEnqueuer(queue, request.id()).start();

        return request;
    }

    private static class RequestEnqueuer extends Thread {
        private final Queue<Integer> queue;
        private final int requestId;

        public RequestEnqueuer(Queue<Integer> queue, int requestId) {
            this.queue = queue;
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
                return;
            }
            queue.add(requestId);
            System.out.printf("[%d] Request ID %d successfully added to queue%n", thread.threadId(), requestId);
        }
    }
}
