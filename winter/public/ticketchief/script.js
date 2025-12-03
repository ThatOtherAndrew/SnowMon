'use strict';

const events = [];
const myTickets = [];
let currentEventId = 0;
let cancelled = false;

/**
 * Generates a cryptographically secure random nonce for replay attack prevention.
 * @returns {string} A unique nonce value
 */
function generateNonce() {
    const array = new Uint8Array(16);
    crypto.getRandomValues(array);
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
}

async function fetchEvents() {
    const response = await fetch('/ticketchief/tickets', {'headers': {'Content-Type': 'application/json'}});
    events.push(...(await response.json()));

    const select = document.getElementById('concert-select');
    select.innerHTML = '';
    for (const [index, event] of events.entries()) {
        const option = document.createElement('option');
        option.value = index.toString();
        option.textContent = `${event['artist']} @ ${event['venue']}`;
        select.appendChild(option);
    }
}

async function selectConcert(event) {
    currentEventId = parseInt(event.target.value);
    await updateTicketInfo();
}

async function purchaseTickets(event) {
    event.preventDefault();
    const button = document.querySelector('.join-queue');
    const dropdown = document.getElementById('concert-select');
    button.disabled = true; // disable button to prevent duplicate purchases
    dropdown.disabled = true; // same thing with concert select


    const ticketCount = document.getElementById('count').valueAsNumber;
    const response = await fetch('/ticketchief/queue', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Nonce': generateNonce(),
        },
        body: JSON.stringify({eventId: currentEventId, tickets: ticketCount}),
    });

    if (response.status === 201) {
        // successfully added to queue
        document.querySelector('.request-id').innerText = (await response.json())['id'];
        document.querySelector('button.cancel').disabled = false;
        await watchQueue(response.headers.get('Location'));
    } else if (response.status === 200) {
        alert('Not enough tickets available!');
    } else if (response.status === 422) {
        alert('Invalid event selected.');
    } else {
        alert(`Sorry, something went wrong. (HTTP ${response.status})`);
    }

    // re-enable inputs
    button.disabled = false;
    dropdown.disabled = false;
}

async function cancelTickets() {
    const requestId = document.querySelector('.request-id').innerText.trim();
    const response = await fetch(`/ticketchief/queue/${requestId}`, {
        method: 'DELETE',
        headers: {'X-Nonce': generateNonce()},
    });

    if (response.status === 204) {
        // successfully cancelled
        cancelled = true;
    } else if (response.status === 409) {
        // tickets already fulfilled, too late
        alert('Cannot cancel purchase, tickets already issued!');
    } else if (response.status === 404) {
        // invalid ticket id
        alert('Ticket purchase request not found.');
    } else {
        // everything else (not good)
        alert(`Sorry, something went wrong. (HTTP ${response.status})`);
    }
}

async function refundTicket(event) {
    const ticketElement = event.target.closest('.ticket');
    const eventId = ticketElement.querySelector('.event-id').innerText;
    const ticketId = ticketElement.querySelector('.ticket-id').innerText;
    const response = await fetch(`/ticketchief/tickets/${eventId}/refund`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Nonce': generateNonce(),
        },
        body: JSON.stringify({ticketIds: [ticketId]}),
    });
    if (response.status === 204) {
        // ticket successfully refunded
        const index = myTickets.findIndex(ticket => ticket.ticketId === ticketId);
        if (index > -1) {
            myTickets.splice(index, 1);
        }
        await updateTicketInfo();
        updateTickets();
    } else if (response.status === 422) {
        // Server-side Event.refundTickets() returned false
        alert('Ticket could not be refunded.');
    } else if (response.status === 404) {
        // eventId is invalid
        alert('Could not find event for ticket.');
    } else {
        // mystery error
        alert(`Sorry, something went wrong. (HTTP ${response.status})`);
    }
}

async function watchQueue(location) {
    const span = document.querySelector('.position');
    let position = -1;
    let json;

    while (position !== 0) {
        // Exit early on cancellation
        if (cancelled) {
            cancelled = false;
            span.innerText = 'Cancelled';
            break;
        }

        const response = await fetch(location, {
            headers: {'Accept': 'application/json'},
        });
        json = await response.json();
        position = json['position'];

        // Exit early on tickets successfully issued
        if (position === 0) {
            span.innerText = 'Tickets issued!';
            break;
        }

        span.innerText = position === -1 ? 'Joining queue...' : position;

        // 500ms poll delay to not spam too many requests
        await new Promise(r => setTimeout(r, 500));
    }

    // tickets issued, so hide cancel button and show ticket IDs on screen
    document.querySelector('button.cancel').disabled = true;
    for (const ticketId of json['ticketIds']) {
        myTickets.push({
            requestId: json['id'],
            eventId: json['eventId'],
            ticketId: ticketId,
        });
    }
    updateTickets();
}

async function updateTicketInfo() {
    const response = await fetch(`/ticketchief/tickets/${currentEventId}`, {
        headers: {'Accept': 'application/json'},
    });
    const json = await response.json();
    document.querySelector('.artist').innerText = json['artist'];
    document.querySelector('.venue').innerText = json['venue'];
    document.querySelector('.datetime').innerText = new Date(json['datetime']);
    document.querySelector('.count').innerText = json['count'];
}

function updateTickets() {
    const ul = document.getElementById('ticket-list');
    ul.innerHTML = '';
    for (const [index, ticket] of myTickets.entries()) {
        const li = document.createElement('li');
        li.innerHTML = `
<div class="ticket">
    <div class="ticket-index">${index + 1}</div>
    <div>
        <h3>Event information</h3>
        <p>
            <b>Artist:</b> <span class="artist">${events[ticket.eventId]['artist']}</span><br/>
            <b>Venue:</b> <span class="venue">${events[ticket.eventId]['venue']}</span><br/>
            <b>Date/Time:</b> <span class="datetime">${new Date(events[ticket.eventId]['datetime']).toLocaleString()}</span><br/>
        </p>
        <h3>Technical details</h3>
        <p>
            <b>Request ID:</b> ${ticket.requestId}<br/>
            <b>Event ID:</b> <span class="event-id">${ticket.eventId}</span><br/>
            <b>Ticket ID:</b> <span class="ticket-id">${ticket.ticketId}</span>
        </p>
    </div>
    <div>
        <button class="button danger">Refund</button>
    </div>
</div>
        `.trim();
        li.querySelector('button').addEventListener('click', refundTicket);
        ul.appendChild(li);
    }
}

function main() {
    fetchEvents().then(() => {
        // update every second
        updateTicketInfo().then(() => setInterval(updateTicketInfo, 1000));

        // get ticket form workin'
        document.getElementById('purchase-form').addEventListener('submit', purchaseTickets);

        // and the cancel button too
        document.querySelector('button.cancel').addEventListener('click', cancelTickets);

        // and now the dropdown as well
        document.getElementById('concert-select').addEventListener('change', selectConcert);
    }).catch((reason) => {
        console.error('Failed to fetch events:', reason);
        alert('Failed to fetch events!');
    })
}

addEventListener('load', main);
