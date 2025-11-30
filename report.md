# Overview

This assignment is an advanced exercise in understanding and implementing the fundamentals of the HTTP protocol, as well developing an understanding of low-level full-stack web development and how to design efficient and robust communications systems between frontend clients and backend servers. This was successfully accomplished, with a fully functional implementation of a mock ticket sales platform.

# Design

## Part 1

### Backend

The backend server is designed around the `HTTPServer` class at its core. This class handles the lifetime of the server including initialising and closing TCP socket connections, and dispatches all incoming requests to the relevant methods for further handling. The design of this implementation drew inspiration from libraries such as Express.js[^1], with a routing API which allows for pattern-matching incoming HTTP requests.

[^1]: https://expressjs.com/

The `Request` class ingests the deserialised data within the received HTTP request (parsed by `HTTPServer.parseHeaders()` and `HTTPServer.parseBody()`). This is then passed to `HTTPServer.routeRequest()`, where the request is dispatched to the correct route handler. A cascading matching algorithm is used, where the order of defined routes is significant, and the first match handles the request and provides a response. If at any stage a parsing error is encountered during deserialisation, a `BadRequestException` is thrown, then gracefully handled at a higher level in `HTTPServer.handleClient()` and an HTTP 400 response is sent to the client, terminating all further processing.

The `Route` class declares the HTTP method to match, as well as the request path. Route parameters can be declared using a colon syntax (e.g. `/route/:id`), where HTTP requests matching the pattern (e.g. `/route/foo`) will be captured and destructured, and added to the request instance. The route parameter can then be accessed with `Request.getRouteParam()`, allowing for an idiomatic developer API for dynamic routes.

When a route is matched, a callback function is called, which takes the parsed HTTP request (as a `Request` instance), now populated with any relevant route parameters. This callback function must return a `Response` instance, containing all the necessary parameters (status code, headers, and body) for rendering the HTTP response message. If a value of null is returned instead, the server continues to match subsequent routes, allowing for futher cascading.

The `Response` class handles the conversion of response data into an RFC 9112 compliant syntax. It also contains a map of common HTTP status codes to human-readable messages, as well as an alternate `Response.HttpCatResponse()` constructor for when the status code is significant but the headers and body are not. A simple HTML template which shows a fun cat photo with the HTTP status as a caption is returned, as an Easter egg for the end user. When the response is rendered to HTML, the `Content-Length` header is dynamically computed based on the size of the body in raw bytes (to ensure compatibility with multibyte UTF-8 / Unicode symbols).

An event-driven architecture was used in the `HTTPServer` class which draws inspiration from various Python API wrappers (e.g. discord.py[^2]). This is reflected in the `onReady()`, `onConnect()`, `onRequest()` and `onResponse()` methods, with default values providing pretty logging functionality by default. The ANSI colour codes for colourful logging are provided in the `ANSI` class, copied and modified from StackOverflow (instead of manual enumeration). The `defaultRoute()` and `errorRoute()` also set default responses to the client if an incoming request has no matching routes or was interrupted by a server error respectively. All these methods are defined with the `protected` modifier such that they can be overridden by a subclass if event-hook-like behaviour is desired.

[^2]: https://discordpy.readthedocs.io/en/stable/

The `events` package handles backend concert state. The `PurchaseManager` class provides the API for the route callbacks to perform actions such as getting event information (serialised into the `Event` class) or requesting a new purchase. The `requestPurchase()` creates a new `PurchaseRequest` instance containing information about the user's request (i.e. the number of tickets to buy, and the chosen concert in Part 2). The mock latency for adding requests to the ticket queue, as well as consuming the requests on the queue, was implemented by two threads to avoid blocking the main thread and causing the web server to be unresponsive to new requests. A `ConcurrentLinkedQueue()` instance was used for lock-free, thread-safe queue access.

A `RequestEnqueuer` thread is instantiated for every purchase request, sleeping for a random interval before adding the purchase request to the end of the queue. Logging is implemented to keep track of the thread's current state. A single `PaymentProcessor` thread is instantiated upon initialisation of the `PurchaseManager` class, which waits for the queue to be populated, sleeps for a random amount as a mock for handling a financial transaction, then fulfils the user's purchase request with a reference to the `PurchaseManager` instance.

The `PurchaseManager.fulfilPurchase()` method is responsible for allocating tickets to the customer after the "payment" is complete. If there are not enough tickets available (i.e. the tickets got sold out while waiting in the queue), a best-effort attempt is made, selling as many tickets as possible to the end user.

JSON parsing was handled using hard-coded regular expressions, to avoid having to implement a full standards-compliant JSON parser (outwith the scope of this assignment's learning intention) or using an external third-party parsing library (disallowed by the specification). This is not the most robust approach as it does not handle e.g. escaped character sequences correctly, nor does it support arbitrary ordering of JSON keys, but it is sufficient for this assignment as we are implementing the client and thus have control over its implementation. JSON responses were simpler, using Java string formatting functionality to fill in values into a JSON string template.

Finally, the `TicketChief` entrypoint class brings it all together, reading the `cs2003-C3.properties` and `tickets.json` files, initialising the HTTP server, and registering the routes for the Ticket Chief API. The `utils.PropertiesReader` helper class is used for parsing the properties file, providing sensible default values for omitted or invalid properties, as well as simple type conversion. Blank lines and comment lines (beginning with `#`) are skipped during parsing.

### Endpoints

#### `GET /tickets`

This endpoint is implemented as per the specification, with a couple of notes:
- An additional response of HTTP 406 (Not Acceptable) is used if the client omits or specifies an incompatible MIME type in the `Accept:` header.
- String length validation is intentionally omitted as the concert data is static and immutable, all provided events are compliant, and truncation is undesirable. The maximum string length in the specification is interpreted as being an input constraint/precondition.

#### `POST /queue`

As above, an HTTP 406 response is sent on an invalid `Accept:` header. Similarly, an HTTP 415 (Unspported Media Type) response is set if the `Content-Type` header is set incorrectly.

#### `GET /queue/:id`

This endpoint was implemented as per the specification with the additional HTTP 406 response as previously discussed.

### Client

The client implementation builds upon the provided template, using a `script.js` file for interactive behaviour. Upon making a ticket purchase request, the `watchQueue()` function is called which calls the `GET /queue/:id` endpoint every 500 milliseconds, updating the UI with queue position data. Once the queue position reaches zero, a new ticket element is constructed and inserted into the document. Concert information is also polled every second using the `setInterval()` function.

An effort has been made to ensure that the frontend UI only ever permits valid actions, to prevent conflicts in state and other confusing UX behaviour. For example, the ticket purchase button is disabled when the user is currently waiting in the ticket queue. However, various server error responses are still checked for, and relevant error messages are displayed to the user via `alert()` popups, in case something goes wrong. This has been intentionally omitted from polled requests, to avoid e.g. spamming the customer with popups every half-second if an error occurs.

## Part 2

The behaviour in Part 2 is implemented as follows:

### Cancelling Purchases

Request cancellation is implemented in the `PurchaseManager.cancelPurchaseRequest()` method. The `RequestEnqueuer` thread is interruptible, and if the thread for the request ID is still running (indicating it has not yet been added to the purchase queue), then the thread is interrupted and purchase request handling is aborted. If it is already on the queue then it is simply removed with `Queue.remove()`. The `PaymentProcessor` thread performs a check at the start and end of processing each request to ensure that the head element of the queue is the same, and skips fulfilling the purchase if there is a mismatch. This handles the case of a request being cancelled when it is first in the queue and already being processed by the `PaymentProcessor` thread. If the purchase has already been fulfilled then the method returns a value of `false` indicating the cancellation has been unsuccessful.

The `DELETE /queue/:id` endpoint is implemented for purchase request cancellation. The response has no HTTP message body, as it uses an HTTP 204 (No Content) response as conventional to indicate a successful DELETE request[^3], which prohibits the use of a body. If the deletion is unsuccessful then an HTTP 409 (Conflict) response is returned indicating conflicting state (already purchased tickets cannot be cancelled, only refunded), and if the purchase request is invalid or not found then a 404 is returned.

[^3]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/204

On the frontend, a `Cancel purchase request` button is added, with a red background to indicate a destructive action. This button is disabled when not in the purchase queue and enabled upon making a purchase request, to again clearly communicate purchase state behaviour and valid actions to the user.

### Refunding Tickets

TODO

### Multiple Concerts

TODO

## Part 3

TODO

# Testing

# Evaluation & Conclusion
